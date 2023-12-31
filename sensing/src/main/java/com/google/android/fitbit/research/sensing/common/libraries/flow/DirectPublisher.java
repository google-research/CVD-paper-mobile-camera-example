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
import com.google.common.flogger.FluentLogger;
import com.google.errorprone.annotations.CheckReturnValue;
import javax.annotation.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

/**
 * Basic thread-safe implementation of {@link Publisher} that publishes data synchronously, directly
 * on the calling thread.
 *
 * <p>This class can be used from within an asynchronous event loop to convert the event into a
 * ReactiveStreams {@link Publisher} that does not queue or buffer events.
 */
@CheckReturnValue
public final class DirectPublisher<T> implements CloseablePublisher<T> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Object lock = new Object();
  @Nullable private Multicaster<T> multicaster = new Multicaster<>();
  @Nullable private Publisher<T> terminalPublisher;

  public DirectPublisher() {}

  /** Subscribes to this Publisher. */
  @Override
  public void subscribe(Subscriber<? super T> subscriber) {
    synchronized (lock) {
      if (multicaster == null) {
        Preconditions.checkNotNull(terminalPublisher);
        terminalPublisher.subscribe(subscriber);
      } else {
        subscriber.onSubscribe(multicaster.createSubscription(subscriber));
      }
    }
  }

  /** Signals all Subscribers with {@code onNext}. */
  public void next(T t) {
    synchronized (lock) {
      if (multicaster == null) {
        logger.atFine().log("Terminal publisher cannot signal onNext");
      } else {
        multicaster.next(t);
      }
    }
  }

  /** Signals all Subscribers with {@code onError} and terminates this Publisher. */
  public void error(Throwable t) {
    synchronized (lock) {
      if (multicaster == null) {
        logger.atFine().log("Terminal publisher cannot signal onError");
      } else {
        multicaster.error(t);
        multicaster = null;
        terminalPublisher = FailedPublisher.create(t);
      }
    }
  }

  /** Signals all Subscribers with {@code onComplete} and terminates this Publisher. */
  public void complete() {
    if (multicaster == null) {
      logger.atFine().log("Terminal publisher cannot signal onComplete");
    } else {
      multicaster.complete();
      multicaster = null;
      terminalPublisher = CompletedPublisher.<T>create();
    }
  }

  /** Equivalent to {@link #complete}. */
  @Override
  public void close() {
    complete();
  }

  public long outstandingRequests() {
    return multicaster.outstandingRequests();
  }

  /**
   * Returns true if this Publisher has signalled {@code onComplete} or {@code onError}. Once
   * terminal, any call to send additional signals will be no-ops, as dictated by Rule 1.7 of the
   * Reactivce Streams spec.
   */
  public boolean isTerminal() {
    synchronized (lock) {
      return terminalPublisher != null;
    }
  }
}
