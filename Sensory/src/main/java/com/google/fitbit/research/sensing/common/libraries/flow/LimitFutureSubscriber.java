package com.google.fitbit.research.sensing.common.libraries.flow;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.errorprone.annotations.CheckReturnValue;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Wraps an existing {@link Subscriber}. Caps the number of {@code #onNext} signals that wrapped
 * {@link Subscriber} can receive. Signals {@link #onComplete} once the limit has been reached.
 */
@CheckReturnValue // see go/why-crv
final class LimitFutureSubscriber<T> implements FutureSubscriber<T, Long> {

  private final Subscriber<T> subscriber;
  private final long limit;
  private final SettableFuture<Long> future;
  private Subscription subscription;

  private long counts;

  LimitFutureSubscriber(long limit, Subscriber<T> subscriber) {
    Preconditions.checkArgument(limit > 0, "Limit must be positive, got %s", limit);
    this.limit = limit;
    this.subscriber = subscriber;
    this.future = SettableFuture.<Long>create();
  }

  @Override
  public void onSubscribe(Subscription s) {
    this.subscription = s;
    subscriber.onSubscribe(s);
  }

  @Override
  public void onNext(T t) {
    if (future.isCancelled()) {
      subscription.cancel();
    } else {
      counts++;
      subscriber.onNext(t);
      if (counts >= limit) {
        subscription.cancel();
        onComplete();
      }
    }
  }

  @Override
  public void onError(Throwable t) {
    future.setException(t);
    subscriber.onError(t);
  }

  @Override
  public void onComplete() {
    future.set(counts);
    subscriber.onComplete();
  }

  /**
   * Resolves with the number of received events when the specific limit has been reached, or if the
   * stream terminates before reaching the limit.
   */
  @Override
  public ListenableFuture<Long> resultFuture() {
    return future;
  }
}