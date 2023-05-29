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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.CheckReturnValue;
import org.reactivestreams.Subscriber;

/**
 * Interface for {@link Subscriber}s that allow the creation of one-off {@link ListenableFuture}s
 * from ReactiveStreams.
 *
 * <p>These Subscribers generally subscribe to a stream and wait for the end of the stream or for a
 * specific condition to occur within the stream. Once this happens, the subscription will be
 * cleaned up and {@link #resultFuture} will resolve.
 *
 * <p>Once resolved, instances of FutureSubscriber should not be resubscribed, as {@link
 * #resultFuture} will remain permanently resolved.
 */
@CheckReturnValue
public interface FutureSubscriber<I, O> extends Subscriber<I> {

  /** Resolves when a specific condition is met, or if the stream terminates. */
  public ListenableFuture<O> resultFuture();
}
