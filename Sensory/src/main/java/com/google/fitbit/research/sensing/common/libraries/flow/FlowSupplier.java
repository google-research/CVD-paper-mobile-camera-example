package com.google.fitbit.research.sensing.common.libraries.flow;

import com.google.errorprone.annotations.CheckReturnValue;
import java.util.concurrent.Callable;

/**
 * General interface for a function that supplies additional results from within a ReactiveStream.
 *
 * <p>This is effectively a {@link java.util.function.Supplier}, but intended to be used from within
 * a ReactiveStreams {@link org.reactivestreams.Subscriber} or {@link
 * org.reactivestreams.Processor}. A common use case is in supplying {@link java.io.File}s to save
 * results from the stream.
 */
@CheckReturnValue // see go/why-crv
public interface FlowSupplier<I, O> {

  /** Supplies a new result in response to an {@code #onNext} signal. */
  public O get(I i) throws Exception;

  /**
   * Performs cleanup functions after the stream has been terminated with {@code #onNext} or {@code
   * #onComplete}.
   */
  default void onTerminate() throws Exception {}

  public static <F, C> FlowSupplier<F, C> fromCallable(Callable<C> callable) {
    return new FlowSupplier<F, C>() {
      @Override
      public C get(F unused) throws Exception {
        return callable.call();
      }
    };
  }
}