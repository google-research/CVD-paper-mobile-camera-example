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

import com.google.errorprone.annotations.CheckReturnValue;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Thread-safe class for multicasting a single signal to multiple {@link Subscriber}s. Can be used
 * when implementing {@link Publisher}s that support multiple Subscribers.
 */
@CheckReturnValue
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
