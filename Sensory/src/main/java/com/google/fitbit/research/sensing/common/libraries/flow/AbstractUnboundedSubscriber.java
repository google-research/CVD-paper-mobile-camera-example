package com.google.fitbit.research.sensing.common.libraries.flow;

import com.google.errorprone.annotations.CheckReturnValue;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

// TODO(b/212072718) - Consider making an interface and using single abstract method.
/**
 * Basic implementation of {@link Subscriber} that requests an unbounded number of values.
 *
 * <p>Clients should extend this implementation and implement an {@link #onNext} method to handle
 * data from the publisher.
 *
 * <p>This class includes empty implementations for the {@link #onError} and {@link onComplete}
 * methods.
 */
@CheckReturnValue // see go/why-crv
public abstract class AbstractUnboundedSubscriber<T> implements Subscriber<T> {

  @Nullable private Subscription subscription;

  protected AbstractUnboundedSubscriber() {}

  /** Requests an unbounded number of events from provided {@link Subscription}. */
  @Override
  public void onSubscribe(Subscription subscription) {
    if (this.subscription != null) {
      this.subscription.cancel();
    }
    this.subscription = subscription;
    subscription.request(Long.MAX_VALUE);
  }

  /**
   * Handles an event sent by a {@link Publisher} in response to requests.
   *
   * <p>Clients are expected to implement this method to handle data for their specific event type.
   */
  @Override
  public abstract void onNext(T t);

  @Override
  public void onError(Throwable throwable) {}

  @Override
  public void onComplete() {}

  public static <T> Subscriber<T> of(Consumer<T> onNextHandler) {
    return new AbstractUnboundedSubscriber<T>() {
      @Override
      public void onNext(T t) {
        onNextHandler.accept(t);
      }
    };
  }
}