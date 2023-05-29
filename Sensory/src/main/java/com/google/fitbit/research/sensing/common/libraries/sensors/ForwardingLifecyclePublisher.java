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

package com.google.fitbit.research.sensing.common.libraries.sensors;

import androidx.annotation.MainThread;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import com.google.fitbit.research.sensing.common.libraries.flow.DeferredPublisher;
import com.google.fitbit.research.sensing.common.libraries.flow.FlowGate;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

/**
 * Simple {@link LifecyclePublisher} implementation that forwards signals from an existing {@link
 * Publisher}.
 *
 * <p>Should be added to one, and only one, {@link androidx.lifecycle.Lifecycle#addObserver}.
 */
public final class ForwardingLifecyclePublisher<T>
    implements LifecyclePublisher<T>, DefaultLifecycleObserver {

  private final DeferredPublisher<T> deferred = new DeferredPublisher<>();
  private final FlowGate whileCreatedGate = FlowGate.createClosed();
  private final Publisher<T> whileCreatedPublisher = whileCreatedGate.passThrough(deferred);
  private final FlowGate whileStartedGate = FlowGate.createClosed();

  ForwardingLifecyclePublisher() {}

  public static <T> ForwardingLifecyclePublisher<T> create() {
    return new ForwardingLifecyclePublisher<>();
  }

  /** Creates and {@link #set}s a new ForwardingLifecyclePublisher. */
  public static <T> ForwardingLifecyclePublisher<T> create(Publisher<T> publisher) {
    ForwardingLifecyclePublisher<T> forwarding = new ForwardingLifecyclePublisher<>();
    forwarding.set(publisher);
    return forwarding;
  }

  /**
   * Begins forwarding signals from the given {@link Publisher}.
   *
   * <p>{@link #set} and {@link #setException} should only be called once. Further calls will be
   * no-ops.
   */
  public void set(Publisher<T> publisher) {
    deferred.set(publisher);
  }

  /**
   * Immediately signals {@code onError} to all existing and future {@link Subscriber}s.
   *
   * <p>{@link #set} and {@link #setException} should only be called once. Further calls will be
   * no-ops.
   */
  public void setException(Exception e) {
    deferred.setException(e);
  }

  @Override
  public void subscribe(Subscriber<? super T> subscriber) {
    whileCreatedPublisher.subscribe(subscriber);
  }

  @Override
  public Publisher<T> untilStop() {
    return whileStartedGate.passThrough(deferred);
  }

  @Override
  @MainThread
  public void onCreate(LifecycleOwner owner) {
    whileCreatedGate.open();
  }

  @Override
  @MainThread
  public void onStart(LifecycleOwner owner) {
    whileStartedGate.open();
  }

  @Override
  @MainThread
  public void onStop(LifecycleOwner owner) {
    whileStartedGate.completeAndClose();
  }

  @Override
  @MainThread
  public void onDestroy(LifecycleOwner owner) {
    whileCreatedGate.completeAndClose();
  }
}
