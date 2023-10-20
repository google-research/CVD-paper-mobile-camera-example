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
import java.util.Optional;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import org.reactivestreams.Subscription;

/**
 * A {@link Subscriber} that resolves a [{@link ListenableFuture} when a matching event is received
 * or when the stream completes.
 */
@CheckReturnValue
public class PredicateFutureSubscriber<T> extends CollectingFutureSubscriber<T, Optional<T>> {

  private final Predicate<T> predicate;
  @Nullable private T result;

  public PredicateFutureSubscriber(Predicate<T> predicate) {
    this.predicate = predicate;
  }

  @Override
  public void init(Subscription subscription) {
    subscription.request(Long.MAX_VALUE);
  }

  @Override
  public boolean accumulate(T value) {
    if (predicate.test(value)) {
      this.result = value;
      return true;
    }
    return false;
  }

  @Override
  public Optional<T> finish() {
    return Optional.ofNullable(result);
  }
}
