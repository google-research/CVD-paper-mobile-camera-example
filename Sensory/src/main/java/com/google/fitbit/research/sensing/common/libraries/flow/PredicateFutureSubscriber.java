package com.google.fitbit.research.sensing.common.libraries.flow;

import com.google.errorprone.annotations.CheckReturnValue;
import java.util.Optional;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import org.reactivestreams.Subscription;

/**
 * A {@link Subscriber} that resolves a [{@link ListenableFuture} when a matching event is received
 * or when the stream completes.
 */
@CheckReturnValue // see go/why-crv
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