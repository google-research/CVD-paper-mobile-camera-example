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

import com.google.common.annotations.VisibleForTesting;
import com.google.errorprone.annotations.CheckReturnValue;
import java.util.ArrayList;
import java.util.List;

/**
 * Convenience methods for managing multiple {@link Subscribed} objects at once.
 *
 * <p>This class is not thread safe. Calls to each method should be externally synchronized.
 */
@CheckReturnValue
public final class SubscribedList<T> {

  @VisibleForTesting final List<Subscribed<T>> list = new ArrayList<>(1);

  public void add(Subscribed<T> subscribed) {
    list.add(subscribed);
  }

  /** Signals all subscriptions with {@code onNext}. */
  @SuppressWarnings("ForeachList")
  public void next(T t) {
    // Iterate manually in case of re-entrancy. If Subscribed.next() results in another
    // synchronous call to SubscribedList.next(), using a for-each loop produces a
    // ConcurrentModificationException. This can occur even if SubscribedList is synchronized
    // externally, since synchronization doesn't prevent re-entrant calls on the same thread.
    for (int i = 0; i < list.size(); i++) {
      list.get(i).next(t);
    }
    list.removeIf(Subscribed::isTerminated);
  }

  /** Signals all subscriptions with {@code onError}. */
  public void error(Throwable t) {
    for (Subscribed<T> s : list) {
      s.error(t);
    }
    list.clear();
  }

  /** Signals all subscriptions with {@code onError}. */
  public void complete() {
    for (Subscribed<T> s : list) {
      s.complete();
    }
    list.clear();
  }

  public long maxRequests() {
    return list.stream().mapToLong(Subscribed::requests).max().orElse(0L);
  }
}
