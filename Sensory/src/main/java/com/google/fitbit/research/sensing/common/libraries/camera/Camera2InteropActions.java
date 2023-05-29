package com.google.fitbit.research.sensing.common.libraries.camera;

import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import androidx.annotation.MainThread;
import androidx.camera.camera2.interop.CaptureRequestOptions;
import androidx.camera.camera2.interop.ExperimentalCamera2Interop;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.fitbit.research.sensing.common.libraries.camera.camera2.Camera2TsvWriters;
import com.google.fitbit.research.sensing.common.libraries.camera.storage.WriteJpegFutureSubscriber;
import com.google.fitbit.research.sensing.common.libraries.flow.FlowFutures;
import com.google.fitbit.research.sensing.common.libraries.storage.StreamToTsvSubscriber;
import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

/**
 * Helper methods for common camera operations.
 */
@ExperimentalCamera2Interop
public final class Camera2InteropActions {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private Camera2InteropActions() {
  }

  /**
   * Locks/unlocks autoexposure and white balance.
   */
  @MainThread
  public static ListenableFuture<Void> setAeAwbLocked(Camera2InteropSensor camera, boolean lock) {
    if (camera.isStarted()) {
      CaptureRequestOptions options =
          new CaptureRequestOptions.Builder()
              .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, lock)
              .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, lock)
              .build();
      return camera.getCamera2Control().addCaptureRequestOptions(options);
    }
    logger.atWarning().log("Camera not started, AE/AWB will not be changed.");
    return Futures.immediateVoidFuture();
  }

  /**
   * Resolves when autoexposure and auto white balance converge.
   */
  public static ListenableFuture<Void> waitForAeAwbConverged(Camera2InteropSensor camera) {
    return Futures.transform(
        FlowFutures.<TotalCaptureResult>whenConditionOrComplete(
            camera.captureResultPublisher().untilStop(),
            capture ->
                capture.get(CaptureResult.CONTROL_AE_STATE)
                    == CameraMetadata.CONTROL_AE_STATE_CONVERGED
                    && capture.get(CaptureResult.CONTROL_AWB_STATE)
                    == CameraMetadata.CONTROL_AWB_STATE_CONVERGED),
        unused -> null,
        MoreExecutors.directExecutor());
  }

  /**
   * Captures a single camera frame as a JPEG file and the corresponding camera parameters as a TSV
   * file. Resolves true when the frame has been captured, or false if the camera closed without the
   * capture.
   */
  public static ListenableFuture<Boolean> captureSingleJpegWithMetadata(
      Camera2InteropSensor camera,
      Callable<File> jpegFile,
      Callable<File> metadataFile,
      Executor writeExecutor) {
    // TODO(b/264280621): Potential race condition where the two streams may not be perfectly
    // aligned. The metadata recorded may not come from the exact camera frame saved.
    // A better solution would be to track the image stream and capture result stream until we
    // find a pair with matching timestamps.
    WriteJpegFutureSubscriber imageSubscriber =
        WriteJpegFutureSubscriber.builder()
            .setWriteExecutor(writeExecutor)
            .setSingleFile(jpegFile)
            .build();
    StreamToTsvSubscriber<CaptureResult> metadataSubscriber =
        StreamToTsvSubscriber.<CaptureResult>builder()
            .setTsvWriter(Camera2TsvWriters.FULL_CAPTURE_RESULT_REQUEST)
            .setSingleFile(metadataFile)
            .setWriteExecutor(writeExecutor)
            .build();
    return Futures.submitAsync(
        () -> {
          SharedImageProxy.asImagePublisher(camera.dataPublisher().untilStop())
              .subscribe(imageSubscriber);
          ListenableFuture<Long> imagesWrittenFuture = imageSubscriber.resultFuture();
          ListenableFuture<Long> metadataWrittenFuture =
              FlowFutures.subscribeWithLimit(
                  camera.captureResultPublisher().untilStop(), metadataSubscriber, /* limit= */ 1);
          return Futures.whenAllSucceed(imagesWrittenFuture, metadataWrittenFuture)
              .call(
                  () ->
                      Futures.getDone(imagesWrittenFuture) == 1
                          && Futures.getDone(metadataWrittenFuture) == 1,
                  writeExecutor);
        },
        writeExecutor);
  }
}