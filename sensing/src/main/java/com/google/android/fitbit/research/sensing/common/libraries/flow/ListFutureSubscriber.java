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
