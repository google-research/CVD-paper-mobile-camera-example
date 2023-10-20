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

package com.google.android.fitbit.research.sensing.common.libraries.flow;

import static java.lang.Math.max;

import com.google.common.math.LongMath;
import com.google.errorprone.annotations.CheckReturnValue;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Allows a parent {@link Subscription} to be expanded into multiple child Subscriptions. Can be
 * used to implement Processors that support multiple Subscribers.
 *
 * <p>Requests are handled lazily. The parent Subscription does not receive any requests until a
 * children Subscription receives requests. If there are multiple children, then requests can be
 * parallelized; one request to the parent will satisfy a single request across all children.
 */
@CheckReturnValue
public final class Distributor<T> {

  private final Subscription parent;
  private final SubscribedList<T> children = new SubscribedList<>();
  private long activeRequests;

  public Distributor(Subscription parent) {
    this.parent = parent;
  }

  /** Creates a child Subscription, to be passed to {@link Subscriber#onSubscribe}. */
  public Subscription createSubscription(Subscriber<? super T> subscriber) {
    synchronized (children) {
      Subscribed<T> subscribed = new Subscribed<T>(subscriber);
      children.add(subscribed);
      return new Subscription() {
        @Override
        public void request(long n) {
          long currentSuscriberRequests = subscribed.request(n);
          if (n > 0) {
            long additionalRequests = currentSuscriberRequests - activeRequests;
            if (additionalRequests > 0) {
              activeRequests = LongMath.saturatedAdd(activeRequests, additionalRequests);
              parent.request(additionalRequests);
            }
          }
        }

        @Override
        public void cancel() {
          subscribed.cancel();
        }
      };
    }
  }

  /** Signals all child subscriptions with {@code onNext}. */
  public void next(T t) {
    synchronized (children) {
      activeRequests = max(0, activeRequests - 1);
      children.next(t);
    }
  }

  /** Signals all child subscriptions with {@code onError}. */
  public void error(Throwable t) {
    synchronized (children) {
      children.error(t);
    }
  }

  /**
   * Signals all Subscribers with {@code onComplete} and cancels all associated {@link
   * Subscriptions}.
   */
  public void complete() {
    synchronized (children) {
      children.complete();
    }
  }
}
