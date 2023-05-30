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

package com.google.fitbit.research.sensing.common.libraries.sensors;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import com.google.errorprone.annotations.CheckReturnValue;
import org.reactivestreams.Publisher;

/** Utility methods for creating lifecycle-aware ReactiveStreams. */
@CheckReturnValue
public final class LifecycleFlows {

  // @SuppressTikTokLint.Handler
  private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

  private LifecycleFlows() {}

  /**
   * Wraps an existing {@link Publisher} to signal {@code onComplete} when the {@link
   * LifecycleOwner} next reaches {@code onStop}.
   *
   * @deprecated Use {@link #untilStop(Publisher, LifecycleOwner, Context)}
   */
  @Deprecated
  public static <T> Publisher<T> untilStop(Publisher<T> publisher, LifecycleOwner lifecycleOwner) {
    ForwardingLifecyclePublisher<T> lifecyclePublisher =
        ForwardingLifecyclePublisher.create(publisher);
    observe(lifecycleOwner, singleUseObserver(lifecyclePublisher));
    return lifecyclePublisher.untilStop();
  }

  public static <T> Publisher<T> untilStop(
      Publisher<T> publisher, LifecycleOwner lifecycleOwner, Context context) {
    ForwardingLifecyclePublisher<T> lifecyclePublisher =
        ForwardingLifecyclePublisher.create(publisher);
    observe(lifecycleOwner, singleUseObserver(lifecyclePublisher));
    return lifecyclePublisher.untilStop();
  }

  /**
   * Converts a {@link Publisher} to a {@link LifecyclePublisher} bound to the given {@link
   * LifecycleOwner}.
   *
   * <p>Subscribers will only receive {@code onNext} between {@code onStart} and {@code onStop}, and
   * {@code onComplete} will be signalled when the bound lifecycle reaches {@code onDestroy}.
   *
   * <p>The wrapped {@link Publisher} will receive a new subscription with {@link Long.MAX_VALUE}
   * requests during {@code onStart} and have this subscription cancelled in {@code onStop}.
   *
   * <p>Unlike {@link LifecycleBound}, the underlying Publisher will not be freed {@code onDestroy}.
   * Anything that subscribes directly will continue to receive data, regardless of whether the
   * lifecycle is stopped or destroyed.
   *
   * @deprecated Use {@link #lifecyclePublisher(Publisher, LifecycleOwner, Context)}.
   */
  @Deprecated
  public static <T> LifecyclePublisher<T> lifecyclePublisher(
      Publisher<T> publisher, LifecycleOwner lifecycleOwner) {
    ForwardingLifecyclePublisher<T> lifecyclePublisher =
        ForwardingLifecyclePublisher.create(publisher);
    observe(lifecycleOwner, lifecyclePublisher);
    return lifecyclePublisher;
  }

  /**
   * Converts a {@link Publisher} to a {@link LifecyclePublisher} bound to the given {@link
   * LifecycleOwner}.
   *
   * <p>Subscribers will only receive {@code onNext} between {@code onStart} and {@code onStop}, and
   * {@code onComplete} will be signalled when the bound lifecycle reaches {@code onDestroy}.
   *
   * <p>The wrapped {@link Publisher} will receive a new subscription with {@link Long.MAX_VALUE}
   * requests during {@code onStart} and have this subscription cancelled in {@code onStop}.
   *
   * <p>Unlike {@link LifecycleBound}, the underlying Publisher will not be freed {@code onDestroy}.
   * Anything that subscribes directly will continue to receive data, regardless of whether the
   * lifecycle is stopped or destroyed.
   */
  public static <T> LifecyclePublisher<T> lifecyclePublisher(
      Publisher<T> publisher, LifecycleOwner lifecycleOwner, Context context) {
    ForwardingLifecyclePublisher<T> lifecyclePublisher =
        ForwardingLifecyclePublisher.create(publisher);
    addObserver(lifecycleOwner, lifecyclePublisher, context);
    return lifecyclePublisher;
  }

  // TODO(b/264280621): Replace Handler/Looper with ContextCompat.getMainExecutor
  // @SuppressTikTokLint.Handler
  private static void observe(LifecycleOwner lifecycleOwner, LifecycleObserver observer) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
      lifecycleOwner.getLifecycle().addObserver(observer);
    } else {
      MAIN_HANDLER.post(() -> lifecycleOwner.getLifecycle().addObserver(observer));
    }
  }

  /**
   * Adds an observer to the {@link LifecycleOwner}.
   *
   * <p>Equivalent to {@code lifecycleOwner.getLifecycle().addObserver(observer)}, but can be called
   * on any thread.
   */
  public static void addObserver(
      LifecycleOwner lifecycleOwner, LifecycleObserver observer, Context context) {
    Lifecycle lifecycle = lifecycleOwner.getLifecycle();
    if (Looper.getMainLooper().isCurrentThread()) {
      lifecycle.addObserver(observer);
    } else {
      ContextCompat.getMainExecutor(context).execute(() -> lifecycle.addObserver(observer));
    }
  }

  private static LifecycleObserver singleUseObserver(DefaultLifecycleObserver observer) {
    return new DefaultLifecycleObserver() {
      @Override
      public void onCreate(LifecycleOwner owner) {
        observer.onCreate(owner);
      }

      @Override
      public void onStart(LifecycleOwner owner) {
        observer.onStart(owner);
      }

      @Override
      public void onStop(LifecycleOwner owner) {
        observer.onStop(owner);
        observer.onDestroy(owner);
        owner.getLifecycle().removeObserver(observer);
      }
    };
  }
}
