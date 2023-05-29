package com.google.fitbit.research.sensing.common.libraries.camera.storage;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static java.lang.Math.min;

import android.annotation.SuppressLint;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import com.google.auto.value.AutoBuilder;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.fitbit.research.sensing.common.libraries.flow.FlowSupplier;
import com.google.fitbit.research.sensing.common.libraries.flow.FutureSubscriber;
import com.google.fitbit.research.sensing.common.libraries.flow.Shared;
import com.google.fitbit.research.sensing.common.libraries.storage.FilesCompat;
import java.io.File;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import javax.annotation.Nullable;
import org.reactivestreams.Subscription;

/**
 * A {@link FutureSubscriber} that records a fixed or unbounded number of images from a stream as
 * separate JPEGs.
 *
 * <p>Once subscribed to a stream, {@code WriteJpegFutureSubscriber} will record every frame until
 * any of the following conditions are met:
 *
 * <ul>
 *   <li>The number of frames specified by {@link Builder#setTotalFrames} has been written
 *   <li>The stream signals {@link #onComplete} or {@link #onError}
 *   <li>{@link #resultFuture} is cancelled
 * </ul>
 *
 * <p>A single instance of {@code WriteJpegFutureSubscriber} should only be subscribed once. After a
 * given instance has finished writing, the same instance will not restart even when re-subscribed
 * to a new stream.
 */
@CheckReturnValue 
public final class WriteJpegFutureSubscriber implements FutureSubscriber<Shared<Image>, Long> {

  private final Executor writeExecutor;
  private final long totalFrames;
  private final long queueSize;
  private final int quality;
  private final FlowSupplier<Long, OutputStream> timestampedOutputStreams;
  private final SettableFuture<Long> resultFuture = SettableFuture.create();

  @Nullable
  private Subscription subscription;
  private FluentFuture<Long> writesCompleted;
  private long framesReceived = 0;

  WriteJpegFutureSubscriber(
      Executor writeExecutor,
      long totalFrames,
      long queueSize,
      int quality,
      FlowSupplier<Long, OutputStream> timestampedOutputStreams) {
    this.writeExecutor = writeExecutor;
    this.totalFrames = totalFrames;
    this.queueSize = queueSize;
    this.quality = quality;
    this.timestampedOutputStreams = timestampedOutputStreams;
    this.writesCompleted = FluentFuture.from(Futures.immediateFuture(0L));
  }

  // setWriteExecutor should be replaced with BackgroundExecutor in TikTok apps.
  @SuppressLint("ConcurrentForbiddenDependencies")
  public static Builder builder() {
    return new AutoBuilder_WriteJpegFutureSubscriber_Builder()
        .setQuality(100)
        .setQueueSize(10L)
        .setWriteExecutor(
            Executors.newSingleThreadExecutor(
                (r) -> {
                  Thread thread = new Thread(r);
                  thread.setName(WriteJpegFutureSubscriber.class.getName() + ".WriteThread");
                  return thread;
                }));
  }

  @Override
  public void onSubscribe(Subscription s) {
    if (resultFuture.isCancelled() || resultFuture.isDone() || framesReceived >= totalFrames) {
      s.cancel();
      return;
    }
    if (this.subscription != null) {
      this.subscription.cancel();
    }
    this.subscription = s;
    this.subscription.request(min(queueSize, totalFrames - framesReceived));
  }

  @Override
  public void onNext(Shared<Image> sharedImage) {
    if (resultFuture.isCancelled() || resultFuture.isDone() || framesReceived >= totalFrames) {
      return;
    }
    try (Shared.Holder<Image> holder = sharedImage.acquire(this)) {
      // Convert Image to YuvImage immediately, then enqueue the YuvImage for writing.
      // This allows the Image to be released before the write completes, which unblocks the camera
      // and allows it to produce the next frame immediately.
      YuvImage yuvImage = ImageEncoders.toYuvImage(holder.get());
      framesReceived++;
      final long imageTimestamp = holder.get().getTimestamp();
      final long writeNumber = framesReceived;
      ListenableFuture<Long> writeFuture =
          Futures.submit(
              () -> {
                if (writeNumber < totalFrames) {
                  // Allow the next camera frame to arrive once we start writing the previous one.
                  subscription.request(1);
                }
                OutputStream outputStream = timestampedOutputStreams.get(imageTimestamp);
                boolean success =
                    yuvImage.compressToJpeg(
                        new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()),
                        quality,
                        outputStream);
                outputStream.close();
                if (!success) {
                  throw new EncodeFailedException(
                      String.format(
                          Locale.US,
                          "Failed to compress YuvImage to JPEG on frame %s of %s",
                          writeNumber,
                          totalFrames));
                }
                return writeNumber;
              },
              writeExecutor);
      // Enqueue the write BEFORE chaining it onto the FluentFuture. We don't want to block the next
      // write on the previous write completing; we just want to keep track if any writes failed
      // at any point.
      this.writesCompleted =
          writesCompleted.transformAsync((unused) -> writeFuture, directExecutor());
      if (framesReceived == totalFrames) {
        subscription.cancel();
        resultFuture.setFuture(writeFuture);
      }
    }
  }

  @Override
  public void onComplete() {
    resultFuture.setFuture(writesCompleted);
  }

  @Override
  public void onError(Throwable t) {
    ListenableFuture<Long> rethrowErrorAfterWritesComplete =
        writesCompleted.transformAsync(
            (unused) -> Futures.immediateFailedFuture(t), directExecutor());
    resultFuture.setFuture(rethrowErrorAfterWritesComplete);
  }

  /**
   * Resolves with the number of frames written after write operations are completed.
   *
   * <p>This is typically equal to {@link Builder#setTotalFrames}, unless the stream was terminated
   * early with {@link #onComplete}.
   *
   * <p>This will resolve in a failure if {@link #onError} was signaled, or if any write failed.
   */
  @Override
  public ListenableFuture<Long> resultFuture() {
    return resultFuture;
  }

  /**
   * Returned by {@link #resultFuture} when JPEG encoding fails.
   */
  public static final class EncodeFailedException extends Exception {

    EncodeFailedException(String msg) {
      super(msg);
    }
  }

  /**
   * Builds a new {@link WriteJpegFutureSubscriber}.
   */
  @AutoBuilder(ofClass = WriteJpegFutureSubscriber.class)
  public abstract static class Builder {

    /**
     * Sets the background Executor responsible for writing to disk. This can be a multi- or
     * single-threaded Executor. If multi-threaded, {@link #setFileSupplier} and
     * {@link #setOutputStreamSupplier} should be thread-safe.
     */
    public abstract Builder setWriteExecutor(Executor executor);

    /**
     * Sets the number of frames to record. To record indefinitely, this can be set to
     * {@link Long.MAX_VALUE}.
     */
    public abstract Builder setTotalFrames(long totalFrames);

    /**
     * Sets the maximum number of frames that can be queued for writing. Frames will be dropped if
     * the queue overflows.
     *
     * <p>This can be set to {@link Long.MAX_VALUE} for an unlimited queue size. This should only
     * be done to guarantee no missed frames on a very short recording, i.e. {@link setTotalFrames}
     * is set to something small. If frames are produced faster than they are written, this will
     * eventually lead to the app throwing an OutOfMemoryException.
     */
    public abstract Builder setQueueSize(long queueSize);

    /**
     * Value from 0 to 100 indicating compression quality.
     */
    public abstract Builder setQuality(int quality);

    /**
     * Sets the {@link OutputStream}s to write to. A new OutputStream should be provided each time
     * the supplier is called, in order to avoid overwriting previously-written images. Each call to
     * {@link FlowSupplier#get} is given the {@link Image#getTimestamp} of the frame being saved.
     */
    public abstract Builder setTimestampedOutputStreams(FlowSupplier<Long, OutputStream> supplier);

    /**
     * Sets the {@link OutputStream}s to write to. A new OutputStream should be provided each time
     * the supplier is called, in order to avoid overwriting previously-written images.
     */
    public Builder setOutputStreamSupplier(Callable<OutputStream> outputStreamSupplier) {
      return this.setTimestampedOutputStreams(FlowSupplier.fromCallable(outputStreamSupplier));
    }

    /**
     * Sets the {@link File}s to write to. A new File should be provided each time the supplier is
     * called, in order to avoid overwriting previously-written images.
     */
    public Builder setFileSupplier(Callable<File> fileSupplier) {
      return this.setOutputStreamSupplier(
          () -> {
            File file = fileSupplier.call();
            file.getParentFile().mkdirs();
            return FilesCompat.newOutputStream(file);
          });
    }

    /**
     * Configures this subscriber to record only a single frame.
     */
    public Builder setSingleOutputStream(Callable<OutputStream> outputStreamSupplier) {
      return this.setTotalFrames(1L).setQueueSize(1L).setOutputStreamSupplier(outputStreamSupplier);
    }

    /**
     * Configures this subscriber to record only a single frame.
     */
    public Builder setSingleFile(Callable<File> fileSupplier) {
      return this.setTotalFrames(1L).setQueueSize(1L).setFileSupplier(fileSupplier);
    }

    /**
     * Configures this subscriber to record only a single frame.
     */
    public Builder setSingleFile(File file) {
      return this.setSingleFile(() -> file);
    }

    /**
     * Configures this subscriber to record to new files in the given folder. Each new file will be
     * named {@code <timestamp>.jpg}, where the timestamp corresponds to
     * {@link Image#getTimestamp}.
     */
    public Builder setTimestampedFiles(Callable<File> folder) {
      return this.setTimestampedOutputStreams(
          new FlowSupplier<Long, OutputStream>() {
            @Override
            public OutputStream get(Long timestamp) throws Exception {
              File parent = folder.call();
              parent.mkdirs();
              return FilesCompat.newOutputStream(new File(parent, timestamp + ".jpg"));
            }
          });
    }

    /**
     * Configures this subscriber to record to new files in the given folder. Each new file will be
     * named {@code <timestamp>.jpg}, where the timestamp corresponds to
     * {@link Image#getTimestamp}.
     */
    public Builder setTimestampedFiles(File folder) {
      return this.setTimestampedFiles(() -> folder);
    }

    /**
     * Configures this subscriber to record
     */
    public abstract WriteJpegFutureSubscriber build();
  }
}