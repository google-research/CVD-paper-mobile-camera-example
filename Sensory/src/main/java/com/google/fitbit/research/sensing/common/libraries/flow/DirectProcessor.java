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
import java.util.function.Function;
import javax.annotation.Nullable;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * A {@link Processor} that applies a synchronous transformation to incoming {@code onNext} signals
 * and multicasts the results to all {@link Subscriber}s with pending requests. {@code onComplete}
 * and {@code onError} signals are forwarded directly to all Subscribers with no change.
 *
 * <p>Transformations should be lightweight. Long-running operations in the transformation can block
 * further upstream signals from being processed.
 *
 * <p>Requests are handled lazily. DirectProcessor does not make any requests to its upstream {@link
 * Publisher} until required by a downstream Subscriber.
 */
@CheckReturnValue
public abstract class DirectProcessor<T, R> implements Processor<T, R> {

  private final Object lock = new Object();
  private final DeferredSubscription incoming = new DeferredSubscription();
  @Nullable private Distributor<R> distributor = new Distributor<>(incoming);
  @Nullable private Publisher<R> terminalPublisher;

  public static <I, O> DirectProcessor<I, O> transformPublisher(
      Publisher<I> publisher, Function<I, O> transform) {
    DirectProcessor<I, O> processor = DirectProcessor.withTransform(transform);
    publisher.subscribe(processor);
    return processor;
  }

  public static <I, O> DirectProcessor<I, O> withTransform(Function<I, O> transform) {
    return new DirectProcessor<I, O>() {
      @Override
      public O process(I input) {
        return transform.apply(input);
      }
    };
  }

  /**
   * Transforms inputs from {@link #onNext} before sending them downstream. Throwing a {@link
   * DirectProcessorException} will signal {@link #onError} and terminate this processor.
   */
  public abstract R process(T input) throws DirectProcessorException;

  @Override
  public void subscribe(Subscriber<? super R> subscriber) {
    synchronized (lock) {
      if (distributor == null) {
        terminalPublisher.subscribe(subscriber);
      } else {
        subscriber.onSubscribe(distributor.createSubscription(subscriber));
      }
    }
  }

  @Override
  public void onSubscribe(Subscription s) {
    synchronized (lock) {
      incoming.set(s);
    }
  }

  @Override
  public void onNext(T t) {
    synchronized (lock) {
      if (distributor != null) {
        try {
          distributor.next(process(t));
        } catch (DirectProcessorException e) {
          onError(e);
        }
      }
    }
  }

  @Override
  public void onComplete() {
    synchronized (lock) {
      distributor.complete();
      distributor = null;
      terminalPublisher = CompletedPublisher.create();
    }
  }

  @Override
  public void onError(Throwable t) {
    synchronized (lock) {
      if (distributor != null) {
        distributor.error(t);
        distributor = null;
        terminalPublisher = FailedPublisher.create(t);
      }
    }
  }

  /** Thrown when an error is encountered during {@link #process}. */
  public static final class DirectProcessorException extends Exception {

    public DirectProcessorException(String message) {
      super(message);
    }

    public DirectProcessorException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
