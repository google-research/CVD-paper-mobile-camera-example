package com.google.fitbit.research.sensing.common.libraries.sensors;

import com.google.errorprone.annotations.CheckReturnValue;
import org.reactivestreams.Publisher;

/**
 * Represents a lifecycle-aware Publisher that terminates based on a bound lifecycle's state.
 *
 * <p>Subscribing directly to this Publisher results in a subscription that signals {@code
 * onComplete} after the associated lifecycle reaches {@code onDestroy}.
 *
 * <p>Subscribing to this Publisher using {@code untilStop().subscribe(subscriber)} will signal
 * {@code onComplete} when the associated lifecycle reaches {@code onStop}.
 */
@CheckReturnValue // see go/why-crv
public interface LifecyclePublisher<T> extends Publisher<T> {

  /**
   * @deprecated Use {@link #untilStop} instead.
   */
  @Deprecated
  default Publisher<T> forSingleLifecycle() {
    return untilStop();
  }

  Publisher<T> untilStop();
}