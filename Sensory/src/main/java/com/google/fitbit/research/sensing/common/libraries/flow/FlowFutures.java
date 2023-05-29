package com.google.fitbit.research.sensing.common.libraries.flow;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.CheckReturnValue;
import java.util.Optional;
import java.util.function.Predicate;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

/**
 * Contains methods for creating one-time {@link ListenableFuture}s from continuous
 * ReactiveStreams.
 */
@CheckReturnValue 
public final class FlowFutures {

  private FlowFutures() {
  }

  public static <T> ListenableFuture<ImmutableList<T>> toList(Publisher<T> publisher, int counts) {
    ListFutureSubscriber<T> subscriber = new ListFutureSubscriber<T>(counts);
    publisher.subscribe(subscriber);
    return subscriber.resultFuture();
  }

  public static <T> ListenableFuture<Optional<T>> whenConditionOrComplete(
      Publisher<T> publisher, Predicate<T> condition) {
    PredicateFutureSubscriber<T> subscriber = new PredicateFutureSubscriber<>(condition);
    publisher.subscribe(subscriber);
    return subscriber.resultFuture();
  }

  /**
   * Subscribes {@code subscriber} to {@code publisher} for up to {@code limit} signals. Once the
   * limit has been reached, signals {@code onComplete}.
   */
  public static <T> ListenableFuture<Long> subscribeWithLimit(
      Publisher<T> publisher, Subscriber<? super T> subscriber, int limit) {
    LimitFutureSubscriber<? super T> limitSubscriber =
        new LimitFutureSubscriber<>(limit, subscriber);
    publisher.subscribe(limitSubscriber);
    return limitSubscriber.resultFuture();
  }
}