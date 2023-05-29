/*
 * Copyright 2022 Google LLC
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

import com.google.errorprone.annotations.CheckReturnValue;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

// TODO(b/212072718) - Consider making an interface and using single abstract method.

/**
 * Basic implementation of {@link Subscriber} that requests an unbounded number of values.
 *
 * <p>Clients should extend this implementation and implement an {@link #onNext} method to handle
 * data from the publisher.
 *
 * <p>This class includes empty implementations for the {@link #onError} and {@link onComplete}
 * methods.
 */
@CheckReturnValue
public abstract class AbstractUnboundedSubscriber<T> implements Subscriber<T> {

  @Nullable private Subscription subscription;

  protected AbstractUnboundedSubscriber() {}

  public static <T> Subscriber<T> of(Consumer<T> onNextHandler) {
    return new AbstractUnboundedSubscriber<T>() {
      @Override
      public void onNext(T t) {
        onNextHandler.accept(t);
      }
    };
  }

  /** Requests an unbounded number of events from provided {@link Subscription}. */
  @Override
  public void onSubscribe(Subscription subscription) {
    if (this.subscription != null) {
      this.subscription.cancel();
    }
    this.subscription = subscription;
    subscription.request(Long.MAX_VALUE);
  }

  /**
   * Handles an event sent by a {@link Publisher} in response to requests.
   *
   * <p>Clients are expected to implement this method to handle data for their specific event type.
   */
  @Override
  public abstract void onNext(T t);

  @Override
  public void onError(Throwable throwable) {}

  @Override
  public void onComplete() {}
}
