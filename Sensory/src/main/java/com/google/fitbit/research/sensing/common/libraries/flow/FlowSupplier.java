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

import com.google.errorprone.annotations.CheckReturnValue;
import java.util.concurrent.Callable;

/**
 * General interface for a function that supplies additional results from within a ReactiveStream.
 *
 * <p>This is effectively a {@link java.util.function.Supplier}, but intended to be used from within
 * a ReactiveStreams {@link org.reactivestreams.Subscriber} or {@link
 * org.reactivestreams.Processor}. A common use case is in supplying {@link java.io.File}s to save
 * results from the stream.
 */
@CheckReturnValue
public interface FlowSupplier<I, O> {

  public static <F, C> FlowSupplier<F, C> fromCallable(Callable<C> callable) {
    return new FlowSupplier<F, C>() {
      @Override
      public C get(F unused) throws Exception {
        return callable.call();
      }
    };
  }

  /** Supplies a new result in response to an {@code #onNext} signal. */
  public O get(I i) throws Exception;

  /**
   * Performs cleanup functions after the stream has been terminated with {@code #onNext} or {@code
   * #onComplete}.
   */
  default void onTerminate() throws Exception {}
}
