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
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import javax.annotation.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

/**
 * {@link Publisher} that can accept {@link Subscriber}s on behalf of another Publisher before the
 * other Publisher is ready.
 *
 * <p>When a Subscriber subscribes to an unset DeferredPublisher, {@code onSubscribe} will be
 * deferred until either {@link #set} or {@link #setException} is called.
 */
public final class DeferredPublisher<T> implements Publisher<T> {

  private final Object lock = new Object();
  private final List<Subscriber<? super T>> deferredSubscribers =
      Collections.synchronizedList(new ArrayList<>());
  @Nullable private Publisher<T> publisher;

  /**
   * Creates a DeferredPublisher from a {@code ListenableFuture<Publisher>}, which can be subscribed
   * to before the final Publisher is available.
   */
  public static <P> DeferredPublisher<P> fromPublisherFuture(
      ListenableFuture<? extends Publisher<P>> future, Executor executor) {
    DeferredPublisher<P> deferred = new DeferredPublisher<>();
    Futures.addCallback(
        future,
        new FutureCallback<Publisher<P>>() {
          @Override
          public void onSuccess(Publisher<P> publisher) {
            deferred.set(publisher);
          }

          @Override
          public void onFailure(Throwable t) {
            deferred.setException(t);
          }
        },
        executor);
    return deferred;
  }

  @Override
  public void subscribe(Subscriber<? super T> subscriber) {
    synchronized (lock) {
      if (publisher != null) {
        publisher.subscribe(subscriber);
        return;
      }
      deferredSubscribers.add(subscriber);
    }
  }

  /** Returns true if {@link #set} or {@link #setException} has been called. */
  public boolean isSet() {
    synchronized (lock) {
      return publisher != null;
    }
  }

  /**
   * Redirects all past and future calls to {@link #subscribe} to the new publisher.
   *
   * <p>No-op if {@link #set}, {@link #setComplete}, or {@link #setException} were previously
   * called.
   */
  public void set(Publisher<T> p) {
    Preconditions.checkNotNull(p);
    synchronized (lock) {
      if (publisher != null) {
        return;
      }
      publisher = p;
    }
    synchronized (deferredSubscribers) {
      for (Subscriber<? super T> s : deferredSubscribers) {
        publisher.subscribe(s);
      }
      deferredSubscribers.clear();
    }
  }

  /**
   * Rejects all previous and future Subscribers by signalling {@code onSubscribe} then {@code
   * onError}.
   *
   * <p>No-op if {@link #set}, {@link #setComplete}, or {@link #setException} were previously
   * called.
   */
  public void setException(Throwable t) {
    Preconditions.checkNotNull(t);
    set(FailedPublisher.create(t));
  }

  /**
   * Signals all previous and future Subscribers with {@code onComplete}.
   *
   * <p>No-op if {@link #set}, {@link #setComplete}, or {@link #setException} were previously
   * called.
   */
  public void setComplete() {
    set(CompletedPublisher.create());
  }
}
