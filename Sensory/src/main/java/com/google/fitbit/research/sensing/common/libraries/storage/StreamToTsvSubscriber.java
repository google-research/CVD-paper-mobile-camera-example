/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.fitbit.research.sensing.common.libraries.storage;

import android.annotation.SuppressLint;
import com.google.auto.value.AutoBuilder;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.fitbit.research.sensing.common.libraries.flow.FlowSupplier;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import javax.annotation.Nullable;
import org.apache.commons.csv.CSVPrinter;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Writes items from a stream to a TSV file.
 *
 * <p>Basic usage:
 *
 * <pre>{@code
 * Subscriber<MotionData> subscriber = StreamToTsvSubscriber.builder()
 *     .setTsvWriter(MotionTsvWriters.XYZ)
 *     .setSingleFile(new File("motion_data.tsv"))
 *     .build();
 * motionSensor.dataPublisher().subscribe(subscriber);
 * }</pre>
 *
 * <p>This example will constantly append data from a stream of motion sensor events to a TSV file
 * until the stream terminates.
 */
@CheckReturnValue
public final class StreamToTsvSubscriber<O> implements Subscriber<O> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final long BUFFER_SIZE = 10;

  private final TsvWriter<O> tsvWriter;
  private final Executor writeExecutor;
  private final long flushInterval;
  private final long eventsPerFile;
  private final FlowSupplier<O, Writer> writerSupplier;

  private long events;
  @Nullable private Subscription subscription;
  private FluentFuture<Optional<CSVPrinter>> writerFuture;

  StreamToTsvSubscriber(
      TsvWriter<O> tsvWriter,
      Executor writeExecutor,
      FlowSupplier<O, Writer> writerSupplier,
      long flushInterval,
      long eventsPerFile) {
    Preconditions.checkArgument(flushInterval > 0, "flushInterval must be positive");
    Preconditions.checkArgument(eventsPerFile > 0, "eventsPerFile must be positive");
    this.tsvWriter = tsvWriter;
    this.writeExecutor = writeExecutor;
    this.writerSupplier = writerSupplier;
    this.writerFuture = FluentFuture.from(Futures.immediateFuture(Optional.empty()));
    this.flushInterval = flushInterval;
    this.eventsPerFile = eventsPerFile;
    this.events = 0;
  }

  // setWriteExecutor should be replaced in TikTok apps
  @SuppressLint("ConcurrentForbiddenDependencies")
  public static <T> Builder<T> builder() {
    return new AutoBuilder_StreamToTsvSubscriber_Builder<T>()
        .setFlushInterval(100)
        .setWriteExecutor(
            Executors.newSingleThreadExecutor(
                (r) -> {
                  Thread thread = new Thread(r);
                  thread.setName(StreamToTsvSubscriber.class.getName() + ".WriterThread");
                  return thread;
                }));
  }

  @Override
  public void onSubscribe(Subscription s) {
    if (subscription != null) {
      subscription.cancel();
    }
    subscription = s;
    subscription.request(BUFFER_SIZE);
  }

  @Override
  public void onNext(O value) {
    final long currentEvent = events;
    events++;
    writerFuture =
        writerFuture
            .transformAsync((w) -> this.getWriter(w, value), writeExecutor)
            .transform(
                writer -> {
                  subscription.request(1);
                  return writeEvent(writer, value, currentEvent);
                },
                writeExecutor);
  }

  @Override
  public void onError(Throwable t) {
    writeExecutor.execute(this::closeWriter);
  }

  @Override
  public void onComplete() {
    writeExecutor.execute(this::closeWriter);
  }

  private ListenableFuture<CSVPrinter> getWriter(Optional<CSVPrinter> writer, O value) {
    if (writer.isPresent()) {
      return Futures.immediateFuture(writer.get());
    }
    return Futures.transformAsync(
        Futures.submit(() -> writerSupplier.get(value), writeExecutor),
        nextWriter -> {
          CSVPrinter newWriter = new CSVPrinter(nextWriter, TsvWriter.tsvFormat());
          newWriter.printRecord(tsvWriter.header());
          return Futures.immediateFuture(newWriter);
        },
        writeExecutor);
  }

  private Optional<CSVPrinter> writeEvent(CSVPrinter writer, O value, long currentEvent) {
    try {
      ImmutableList<String> row = tsvWriter.row(value);
      for (List<String> rowPart : Iterables.partition(row, tsvWriter.rowWrap())) {
        writer.printRecord(rowPart);
      }
      if (currentEvent % eventsPerFile == (eventsPerFile - 1)) {
        writer.flush();
        writer.close();
        return Optional.empty();
      }
      if (currentEvent % flushInterval == (flushInterval - 1)) {
        writer.flush();
      }
      return Optional.of(writer);
    } catch (IOException e) {
      logger.atSevere().withCause(e).log("Failed to write to file");
      return Optional.empty();
    }
  }

  private void closeWriter() {
    writerFuture =
        writerFuture.transformAsync(
            writer -> {
              if (writer.isPresent()) {
                try {
                  writer.get().flush();
                  writer.get().close();
                } catch (IOException e) {
                  logger.atSevere().withCause(e).log("Failed to close file");
                }
              }
              writerSupplier.onTerminate();
              return Futures.immediateFuture(Optional.empty());
            },
            writeExecutor);
  }

  /** Builder for {@link StreamToTsvSubscriber}. */
  @AutoBuilder(ofClass = StreamToTsvSubscriber.class)
  public abstract static class Builder<O> {

    private static Writer getFileWriter(File file) throws IOException {
      file.getParentFile().mkdirs();
      return FilesCompat.newBufferedWriter(file);
    }

    /** Defines columns for the TSV file. */
    public abstract Builder<O> setTsvWriter(TsvWriter<O> tsvWriter);

    /**
     * Sets the background Executor that disk writes will be performed on. To avoid out-of-order
     * rows, this should be a single-threaded or sequential executor.
     */
    public abstract Builder<O> setWriteExecutor(Executor writeExecutor);

    /**
     * Sets the subscriber to automatically chunk the stream into multiple files. {@code
     * fileSupplier} should return a new, unique file each time it is called, otherwise previous
     * files will be overwitten with later data.
     */
    public Builder<O> setFileChunks(Callable<File> fileSupplier, long eventsPerFile) {
      return this.setWriterChunks(() -> getFileWriter(fileSupplier.call()), eventsPerFile);
    }

    /**
     * Sets the subscriber to automatically chunk the stream into multiple files. {@code
     * writeSupplier} should return a writer with a new destination each time it is called in order
     * to properly chunk data.
     */
    public Builder<O> setWriterChunks(Callable<Writer> writerSupplier, long eventsPerFile) {
      return this.setWriterSupplier(writerSupplier).setEventsPerFile(eventsPerFile);
    }

    /**
     * Sets the subscriber to automatically chunk the stream into multiple files. {@code
     * writeSupplier.get()} should return a writer with a new destination each time it is called in
     * order to properly chunk data.
     */
    public Builder<O> setWriterChunks(FlowSupplier<O, Writer> writerSupplier, long eventsPerFile) {
      return this.setWriterSupplier(writerSupplier).setEventsPerFile(eventsPerFile);
    }

    /** Sets the subscriber to write all data to a single File. */
    public Builder<O> setSingleFile(File file) {
      return this.setSingleFile(() -> file);
    }

    /** Sets the subscriber to write all data to a single File. */
    public Builder<O> setSingleFile(Callable<File> fileSuppler) {
      return this.setWriterSupplier(() -> getFileWriter(fileSuppler.call()))
          .setEventsPerFile(Long.MAX_VALUE);
    }

    /** Sets the subscriber to write all data to a single Writer. */
    public Builder<O> setSingleWriter(Writer writer) {
      return this.setWriterSupplier(() -> writer).setEventsPerFile(Long.MAX_VALUE);
    }

    /**
     * Sets the subscriber to write to a new File for every single event. {@code fileSupplier}
     * should return a new, unique file each time it is called, otherwise previous files will be
     * overwritten with later data.
     */
    public Builder<O> setFilePerEvent(Callable<File> fileSupplier) {
      return this.setFileChunks(fileSupplier, 1);
    }

    /**
     * Sets the subscriber to write to a new Writer for every single event. {@code writerSupplier}
     * should return a writer with a new destination each time it is called.
     */
    public Builder<O> setWriterPerEvent(Callable<Writer> writerSupplier) {
      return this.setWriterChunks(writerSupplier, 1);
    }

    /**
     * Sets the subscriber to write to a new Writer for every single event. {@code
     * writerSupplier.get()} should return a writer with a new destination each time it is called.
     */
    public Builder<O> setWriterPerEvent(FlowSupplier<O, Writer> writerSupplier) {
      return this.setWriterChunks(writerSupplier, 1);
    }

    /**
     * Sets the number of events accumulated before being flushed to disk.
     *
     * <p>Setting a high interval will reduce the frequency of writes at the cost of greater memory
     * footprint and greater risk of data loss if the app crashes before events are flushed.
     */
    public abstract Builder<O> setFlushInterval(long flushInterval);

    /** Set writer supplier */
    Builder<O> setWriterSupplier(Callable<Writer> writerSupplier) {
      return setWriterSupplier(FlowSupplier.<O, Writer>fromCallable(writerSupplier));
    }

    abstract Builder<O> setWriterSupplier(FlowSupplier<O, Writer> writerSupplier);

    abstract Builder<O> setEventsPerFile(long eventsPerFile);

    public abstract StreamToTsvSubscriber<O> build();
  }
}
