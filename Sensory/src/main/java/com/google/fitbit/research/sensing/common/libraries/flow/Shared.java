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
import org.reactivestreams.Subscriber;

/**
 * Counts the number of ongoing holds for an {@link AutoCloseable} object and closes the object when
 * no more holds remain.
 *
 * <p>In the context of ReactiveStreams, Publishers should wrap the AutoCloseable in Shared before
 * publishing, and {@link #release} immediately after. Subscribers should {@link #acquire}
 * immediately upon receiving the object in {@code onNext}. Synchronous subscribers should {@link
 * #release} before returning from onNext, while asynchronous subscribers should {@link #release}
 * whenever asynchronous handling is complete. This ensures that Subscribers will not lose access to
 * the underlying resource before they are finished using it.
 *
 * <p>While {@link Shared} itself is thread-safe, the underlying object may not be thread-safe.
 */
@CheckReturnValue
public interface Shared<S extends AutoCloseable> {

  /** Acquire a hold. The underlying resource will not be released until all holds are released. */
  public Holder<S> acquire(Subscriber<?> key);

  /** Release a hold that was previously {@link #acquire}d. */
  public void release(Subscriber<?> key);

  /**
   * Holds an {@link Shared} object. Closing this Holder is equivalent to calling {@link
   * Shared#release} with the same key.
   *
   * <p>Example usage
   *
   * <pre>{@code
   * public void onNext(Shared<File> sharedFile) {
   *   try (Shared<File>.Holder holder = sharedFile.acquire(key)) {
   *     File file = holder.get();
   *     // Do stuff with the file
   *   }
   *   // Hold is automatically released when exiting the try-with block. If no other keys are left,
   *   // the file is closed.
   * }
   * }</pre>
   */
  interface Holder<H extends AutoCloseable> extends AutoCloseable {

    public H get();

    /** Releases the current hold. Equivalent to calling {@link Shared#release}. */
    @Override
    public void close();
  }
}
