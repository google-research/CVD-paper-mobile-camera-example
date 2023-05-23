package com.google.fitbit.research.sensing.common.libraries.flow;

import com.google.common.annotations.VisibleForTesting;
import com.google.errorprone.annotations.CheckReturnValue;
import java.util.ArrayList;
import java.util.List;

/**
 * Convenience methods for managing multiple {@link Subscribed} objects at once.
 *
 * <p>This class is not thread safe. Calls to each method should be externally synchronized.
 */
@CheckReturnValue // see go/why-crv
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