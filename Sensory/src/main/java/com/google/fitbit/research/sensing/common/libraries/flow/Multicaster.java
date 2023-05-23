package com.google.fitbit.research.sensing.common.libraries.flow;

import com.google.errorprone.annotations.CheckReturnValue;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Thread-safe class for multicasting a single signal to multiple {@link Subscriber}s. Can be used
 * when implementing {@link Publisher}s that support multiple Subscribers.
 */
@CheckReturnValue // see go/why-crv
public final class Multicaster<T> {

  private final SubscribedList<T> list = new SubscribedList<>();

  public Multicaster() {}

  /** Creates a new {@link Subscription} that can be passed to {@link Subscriber#onSubscribe}. */
  public Subscription createSubscription(Subscriber<? super T> subscriber) {
    synchronized (list) {
      Subscribed<T> subscribed = new Subscribed<T>(subscriber);
      list.add(subscribed);
      return new Subscription() {
        @Override
        public void request(long n) {
          subscribed.request(n);
        }

        @Override
        public void cancel() {
          subscribed.cancel();
        }
      };
    }
  }

  /** Signals all subscriptions with {@code onNext}. */
  public void next(T t) {
    synchronized (list) {
      list.next(t);
    }
  }

  /** Signals all subscriptions with {@code onError}. */
  public void error(Throwable t) {
    synchronized (list) {
      list.error(t);
    }
  }

  /** Signals all subscriptions with {@code onComplete}. */
  public void complete() {
    synchronized (list) {
      list.complete();
    }
  }

  public long outstandingRequests() {
    return list.maxRequests();
  }
}