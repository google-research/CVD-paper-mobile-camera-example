/*
 * Copyright 2023 Google LLC
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

package com.google.fitbit.research.sensing.common.libraries.flow;

import com.google.common.annotations.VisibleForTesting;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Hot-swappable Publisher that allows the upstream Publisher to be changed or removed, even while
 * there are downstream Subscribers.
 *
 * <p>Subscribers to SwappablePublisher will receives any signals sent by the current upstream
 * Publisher set by {@link #setPublisher}, including termination signals.
 *
 * <p>The current Publisher can be replaced with a new Publisher, which effectively transfers all
 * subscriptions to the new Publisher. From a Subscriber's perspective, this operation is entirely
 * transparent. All pending requests will be transferred to the new Publisher, and the Subscriber
 * will stop receiving signals from the old Publisher and begin receiving signals from the new
 * Publisher.
 *
 * <p>If an upstream Publisher signals termination with {@code onComplete} or {@code onError}, or if
 * {@link #setException} or {@link #setComplete} are called, then subscriptions will remain
 * terminated even if the terminated Publisher is replaced.
 */
public final class SwappablePublisher<T> implements CloseablePublisher<T> {

  private final Set<SwappableSubscription> downstreamSubscriptions =
      Collections.synchronizedSet(new HashSet<>());
  private DeferredPublisher<T> upstreamPublisher = new DeferredPublisher<>();

  private SwappablePublisher() {}

  public static <T> SwappablePublisher<T> create() {
    return new SwappablePublisher<T>();
  }

  public static <T> SwappablePublisher<T> create(Publisher<T> initialPublisher) {
    SwappablePublisher<T> publisher = new SwappablePublisher<T>();
    publisher.setPublisher(initialPublisher);
    return publisher;
  }

  @Override
  public void subscribe(Subscriber<? super T> subscriber) {
    SwappableSubscription subscription = new SwappableSubscription(subscriber);
    synchronized (downstreamSubscriptions) {
      downstreamSubscriptions.add(subscription);
      // Subscribe to the upstream publisher to forward signals to the subscriber.
      subscription.subscribeUpstream(upstreamPublisher);
    }
    // Create a subscription for the downstream subscriber for forwarding request and cancel to the
    // upstream publisher.
    subscriber.onSubscribe(subscription);
  }

  /**
   * Replaces the current upstream Publisher.
   *
   * <p>While this is safe to use with asynchronous Publishers, {@link #setPublisher} and {@link
   * #removePublisher} are not thread safe and should only be called serially.
   */
  public void setPublisher(Publisher<T> publisher) {
    // Unsubscribe from the existing upstream publisher.
    removePublisher();
    // Start subscriptions to the new upstream publisher.
    upstreamPublisher.set(publisher);
  }

  /**
   * Terminates all current and future Subscibers with {@code onComplete}. All subsequent calls to
   * {@link #setPublisher} are no-ops.
   */
  public void setComplete() {
    setPublisher(CompletedPublisher.create());
  }

  /**
   * Terminates all current and future Subscribers with {@code onError}. All subsequent calls to
   * {@link #setPublisher} are no-ops.
   */
  public void setException(Throwable t) {
    setPublisher(FailedPublisher.create(t));
  }

  /** Equivalent to {@link #setComplete}. */
  @Override
  public void close() {
    setComplete();
  }

  /**
   * Removes the current upstream Publisher. Cancels the existing Publisher's subscriptions. Signals
   * will eventually pause, then resume when {@link #setPublisher} is called with a new Publisher.
   *
   * <p>While this is safe to use with asynchronous Publishers, {@link #setPublisher} and {@link
   * #removePublisher} are not thread safe and should only be called serially.
   */
  public void removePublisher() {
    if (upstreamPublisher.isSet()) {
      synchronized (downstreamSubscriptions) {
        for (SwappableSubscription s : downstreamSubscriptions) {
          s.clearUpstream();
        }
        upstreamPublisher = new DeferredPublisher<>();
        for (SwappableSubscription s : downstreamSubscriptions) {
          s.subscribeUpstream(upstreamPublisher);
        }
      }
    }
  }

  @VisibleForTesting
  int numCurrentSubscribers() {
    return downstreamSubscriptions.size();
  }

  /**
   * Subscription that passes signals from an upstream Publisher to a downstream Subscriber. The
   * upstream Publisher can be removed or changed at any time without the downstream Subscriber
   * knowing.
   */
  private final class SwappableSubscription implements Subscription {

    private final Subscribed<T> subscribed;
    private final AtomicReference<Subscription> upstream;

    SwappableSubscription(Subscriber<? super T> subscriber) {
      this.subscribed = new Subscribed<T>(subscriber);
      this.upstream = new AtomicReference<>(null);
    }

    @Override
    public void request(long n) {
      subscribed.request(n);

      Subscription s = upstream.get();
      if (s != null) {
        s.request(n);
      }
    }

    @Override
    public void cancel() {
      downstreamSubscriptions.remove(this);
      subscribed.cancel();
      clearUpstream();
    }

    void clearUpstream() {
      Subscription s = upstream.getAndSet(null);
      if (s != null) {
        s.cancel();
      }
    }

    void subscribeUpstream(Publisher<T> upstreamPublisher) {
      if (subscribed.isTerminated()) {
        downstreamSubscriptions.remove(this);
        return;
      }

      Subscriber<T> forwardUpstreamToDownstream =
          new Subscriber<>() {

            @Override
            public void onSubscribe(Subscription subscription) {
              if (subscribed.isTerminated()) {
                subscription.cancel();
                cancel();
                return;
              }

              // Cancel the previous subscription.
              Subscription previous = upstream.getAndSet(subscription);
              if (previous != null) {
                previous.cancel();
              }

              // Forward unfilled requests from the previous subscription.
              long requests = subscribed.requests();
              if (requests > 0) {
                subscription.request(requests);
              }
            }

            @Override
            public void onNext(T value) {
              subscribed.next(value);
            }

            @Override
            public void onComplete() {
              subscribed.complete();
              // Stop tracking this subscription since the downstream subscriber is now terminated
              // and can no longer receive signals
              cancel();
            }

            @Override
            public void onError(Throwable t) {
              subscribed.error(t);
              // Stop tracking this subscription since the downstream subscriber is now terminated
              // and can no longer receive signals
              cancel();
            }
          };
      upstreamPublisher.subscribe(forwardUpstreamToDownstream);
    }
  }
}
