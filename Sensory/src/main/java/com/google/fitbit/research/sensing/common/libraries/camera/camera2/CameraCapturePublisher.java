package com.google.fitbit.research.sensing.common.libraries.camera.camera2;

import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.fitbit.research.sensing.common.libraries.flow.DirectProcessor;
import com.google.fitbit.research.sensing.common.libraries.flow.DirectPublisher;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

/**
 * Publishes {@link CaptureRequest}s and {@link CaptureResult}s from a camera session.
 */
@CheckReturnValue 
public final class CameraCapturePublisher extends CameraCaptureSession.CaptureCallback
    implements Publisher<TotalCaptureResult> {

  private final DirectPublisher<TotalCaptureResult> internalPublisher = new DirectPublisher<>();

  public CameraCapturePublisher() {
  }

  public static Publisher<CaptureRequest> asRequests(Publisher<? extends CaptureResult> results) {
    return DirectProcessor.transformPublisher(results, CaptureResult::getRequest);
  }

  @Override
  public void onCaptureCompleted(
      CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
    internalPublisher.next(result);
  }

  @Override
  public void subscribe(Subscriber<? super TotalCaptureResult> subscriber) {
    internalPublisher.subscribe(subscriber);
  }
}