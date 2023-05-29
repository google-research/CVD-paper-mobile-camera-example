package com.google.fitbit.research.sensing.common.libraries.flow;

import static java.lang.Math.max;

import com.google.common.math.LongMath;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Holds the state of a {@link Subscription}: the current {@link Subscriber}, number of requests,
 * and whether or not the subscription is still active.
 */
public class Subscribed<S> {

  private final AtomicLong requests;
  private Optional<Subscriber<? super S>> subscriber;

  public Subscribed(Subscriber<? super S> subscriber) {
    this.subscriber = Optional.of(subscriber);
    this.requests = new AtomicLong(0);
  }

  public long requests() {
    return subscriber.isPresent() ? requests.get() : 0;
  }

  /**
   * Returns the {@link Subscriber} if the subscription is still active.
   */
  @Nullable
  Subscriber<? super S> getSubscriber() {
    return subscriber.orElse(null);
  }

  /**
   * Adds additional requests to the subscription and returns the total number of pending requests.
   */
  @CanIgnoreReturnValue
  public long request(long n) {
    if (n <= 0) {
      error(new IllegalArgumentException("Must request a positive number of items: " + n));
      return requests.get();
    } else {
      return requests.updateAndGet(l -> LongMath.saturatedAdd(l, n));
    }
  }

  /**
   * Terminates the subscription.
   */
  public void cancel() {
    synchronized (this) {
      subscriber = Optional.empty();
    }
  }

  /**
   * Signals {@link Subscriber#onNext} if there are pending requests, returning true if the signal
   * was delievered.
   */
  @CanIgnoreReturnValue
  public boolean next(S s) {
    /*
     * Calling Subscriber methods from within a synchronized method would typically be discouraged;
     * invoking an alien method in this manner risks deadlocks. This can be demonstrated
     * with a simple Subscriber that re-acquires the lock and blocks on a different thread:
     *
     * new Subscriber() {
     *   @Override
     *   public void onNext() {
     *     Executors.newSingleThreadExecutor(subscribed::complete).get();
     *   }
     * }
     *
     * However, ReactiveStreams Rule 2.2 states that Subscribers should not block the Publisher.
     * Consequently, we don't expect deadlocks to arise from Subscribers that adhere to this rule.
     */
    long numPending = requests.getAndUpdate(x -> max(x - 1, 0));
    if (numPending > 0) {
      synchronized (this) {
        subscriber.ifPresent(sub -> sub.onNext(s));
        return subscriber.isPresent();
      }
    }
    return false;
  }

  /**
   * Signals {@link Subscriber#onError} and marks this subscription as terminated.
   */
  public void error(Throwable t) {
    synchronized (this) {
      Optional<Subscriber<? super S>> current = subscriber;
      subscriber = Optional.empty();
      current.ifPresent(sub -> sub.onError(t));
    }
  }

  /**
   * Signals {@link Subscriber#onComplete} and marks this subscription as terminated.
   */
  public void complete() {
    synchronized (this) {
      Optional<Subscriber<? super S>> current = subscriber;
      subscriber = Optional.empty();
      current.ifPresent(Subscriber::onComplete);
    }
  }

  public boolean isTerminated() {
    return subscriber.isEmpty();
  }
}