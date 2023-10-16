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

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.CheckReturnValue;
import java.util.Optional;
import java.util.function.Predicate;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

/**
 * Contains methods for creating one-time {@link ListenableFuture}s from continuous ReactiveStreams.
 */
@CheckReturnValue
public final class FlowFutures {

  private FlowFutures() {}

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
