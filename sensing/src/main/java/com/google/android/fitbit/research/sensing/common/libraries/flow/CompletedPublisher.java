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

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/** Publisher that immediately terminates all Subscribers with {@code onComplete}. */
public final class CompletedPublisher<T> implements Publisher<T> {

  private static final Subscription NO_OP_SUBSCRIPTION =
      new Subscription() {
        @Override
        public void request(long n) {}

        @Override
        public void cancel() {}
      };

  private CompletedPublisher() {}

  public static <T> CompletedPublisher<T> create() {
    return new CompletedPublisher<T>();
  }

  @Override
  public void subscribe(Subscriber<? super T> subscriber) {
    // Reactive Streams Rule 1.12:
    // "Publisher.subscribe MUST call onSubscribe on the provided Subscriber prior to any other
    // signals to that Subscriber"
    // https://github.com/reactive-streams/reactive-streams-jvm#1-publisher-code
    subscriber.onSubscribe(NO_OP_SUBSCRIPTION);
    subscriber.onComplete();
  }
}
