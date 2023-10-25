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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.errorprone.annotations.CheckReturnValue;
import javax.annotation.Nullable;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Abstract base class for {@link Subscriber}s that collect items from a ReactiveStream.
 *
 * <p>Instances of CollectingFutureSubscriber can be attached to a stream, accumulate values until
 * specific conditions are met or until the stream completes, then resolves an associated {@link
 * ListenableFuture} with the accumulated result.
 *
 * <p>Instances of CollectingFutureSubscriber should generally only subscribe once. Changing
 * subscriptions for an unresolved CollectingFutureSubscriber will reset accumulated state, but this
 * can be prone to race conditions and is best avoided by not re-using instances of
 * CollectingFutureSubscriber.
 */
@CheckReturnValue
public abstract class CollectingFutureSubscriber<T, R> implements FutureSubscriber<T, R> {

  private final SettableFuture<R> resultFuture = SettableFuture.<R>create();
  @Nullable private Subscription subscription;

  public CollectingFutureSubscriber() {}

  @Override
  public void onSubscribe(Subscription subscription) {
    if (this.subscription != null) {
      this.subscription.cancel();
    }
    if (!resultFuture.isCancelled() && !resultFuture.isDone()) {
      this.subscription = subscription;
      init(subscription);
    } else {
      subscription.cancel();
    }
  }

  @Override
  public void onNext(T value) {
    if (resultFuture.isCancelled() || resultFuture.isDone()) {
      subscription.cancel();
    } else if (accumulate(value)) {
      subscription.cancel();
      resultFuture.set(finish());
    }
  }

  @Override
  public void onError(Throwable throwable) {
    resultFuture.setException(throwable);
  }

  @Override
  public void onComplete() {
    resultFuture.set(finish());
  }

  /**
   * Completes when the stream signals {@link #onComplete} or when {@link #accumulate} returns
   * {@code true} with the value from {@link #finish}. Fails if {@link #onError} is signalled.
   */
  @Override
  public ListenableFuture<R> resultFuture() {
    return resultFuture;
  }

  /**
   * Called when this Subscriber first attaches to a stream. Subclasses should make requests to the
   * subscription and initialize containers for subsequent calls to {@link #accumulate}.
   */
  protected abstract void init(Subscription subscription);

  /**
   * Called when a value is received from a stream. Should return {@code true} when ready to resolve
   * {@link #resultFuture} and {@code false} otherwise.
   */
  protected abstract boolean accumulate(T value);

  /**
   * Called when the stream completes or when {@link #accumulate} returns {@code true}. Should
   * return the final value used to resolve {@link #resultFuture}.
   */
  protected abstract R finish();
}
