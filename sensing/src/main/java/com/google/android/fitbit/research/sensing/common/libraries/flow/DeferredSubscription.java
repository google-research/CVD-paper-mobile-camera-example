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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.math.LongMath;
import com.google.errorprone.annotations.CheckReturnValue;
import javax.annotation.Nullable;
import org.reactivestreams.Subscription;

/**
 * {@link Subscription} that allows requests to be made before an upstream Subscription is attached.
 *
 * <p>DeferredSubscription can be used to implement Processors that allow adding Subscribers before
 * the Processor itself is subscribed to a Publisher:
 *
 * <pre>{@code
 * processor = new Processor() {
 *   DeferredSubscription deferred = new DeferredSubscription();
 *
 *   @Override
 *   public void subscribe(Subscriber subscriber) {
 *     subscriber.onSubscribe(deferred);
 *   }
 *
 *   @Override
 *   public void onSubscribe(Subscription subscription) {
 *     deferred.set(subscription);
 *   }
 * };
 *
 * processor.subscribe(subscriber);
 * subscriber.request(10L);
 * // Publisher should automatically receive 10 requests on Subscription
 * publisher.subscribe(processor);
 * }</pre>
 */
@CheckReturnValue
public class DeferredSubscription implements Subscription {

  @Nullable private Subscription subscription;
  private boolean cancelled;
  private long requests;

  public DeferredSubscription() {}

  @Override
  public synchronized void request(long n) {
    if (subscription != null) {
      subscription.request(n);
    } else if (!cancelled && n > 0) {
      requests = LongMath.saturatedAdd(requests, n);
    }
  }

  @Override
  public synchronized void cancel() {
    if (subscription != null) {
      subscription.cancel();
    } else {
      cancelled = true;
    }
  }

  public synchronized void set(Subscription s) {
    checkNotNull(s);
    checkState(this.subscription == null, "Cannot replace an existing subscription");
    this.subscription = s;
    if (cancelled) {
      s.cancel();
    } else if (requests > 0) {
      s.request(requests);
    }
  }
}
