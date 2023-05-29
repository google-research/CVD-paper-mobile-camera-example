package com.google.fitbit.research.sensing.common.libraries.flow;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CheckReturnValue;
import org.reactivestreams.Subscription;

/**
 * A {@link Subscriber} that collects a fixed number of signals from a stream and accumulates values
 * into a {@link ImmutableList}.
 */
@CheckReturnValue 
public class ListFutureSubscriber<T> extends CollectingFutureSubscriber<T, ImmutableList<T>> {

  private final int numItems;
  private ImmutableList.Builder<T> items;
  private int accumulated = 0;

  public ListFutureSubscriber(int numItems) {
    Preconditions.checkArgument(numItems > 0, "numItems must be greater than 0, got %s", numItems);
    this.numItems = numItems;
  }

  @Override
  public void init(Subscription subscription) {
    items = ImmutableList.<T>builder();
    subscription.request(numItems);
  }

  @Override
  public boolean accumulate(T value) {
    accumulated++;
    items.add(value);
    return accumulated >= numItems;
  }

  @Override
  public ImmutableList<T> finish() {
    return items.build();
  }
}