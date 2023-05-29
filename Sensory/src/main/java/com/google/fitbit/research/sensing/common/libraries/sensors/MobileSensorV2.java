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

import android.content.Context;
import androidx.activity.ComponentActivity;
import androidx.annotation.MainThread;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleService;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/**
 * Common interface for lifecycle-bound streams that read from a device's sensors, such as the
 * microphone or accelerometer.
 */
public interface MobileSensorV2<T> extends LifecycleOwner {

  /**
   * Indicates whether this sensor has been started and {@link #dataPublisher} is producing data.
   * This can only be true if the lifecycle from {@link Builder#setBoundLifecycle} has also reached
   * {@code onStart}.
   */
  @MainThread
  default boolean isStarted() {
    return getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED);
  }

  /**
   * Returns this sensor's lifecycle, which roughly follows the lifecycle bound in {@link
   * Builder#setBoundLifecycle}.
   *
   * <p>This lifecycle can be observed to see the sensor's current state:
   *
   * <ul>
   *   <li>{@code onCreate}: Sensor has been prepared and is ready to use. Can only happen after the
   *       bound lifecycle also reaches {@code onCreate}.
   *   <li>{@code onStart}: Sensor has started. {@link #dataPublisher} is actively producing data.
   *       Can only happen after the bound lifecycle also reaches {@code onStart}.
   *   <li>{@code onStop}: Sensor has stopped. {@link #dataPublisher} will stop producing data.
   *   <li>{@code onDestroy}: Sensor has been released and can no longer be used. All current and
   *       future Subscribers to {@link #dataPublisher} will receive {@code onComplete}. Occurs when
   *       the bound lifecycle also reaches {@code onDestroy}.
   * </ul>
   */
  @Override
  Lifecycle getLifecycle();

  /**
   * Publisher for this sensor's primary data stream. Signals {@code onNext} while {@link
   * #getLifecycle} is between {@code onStart} and {@code onStop}.
   *
   * <p>There are multiple ways to subscribe to this stream:
   *
   * <ul>
   *   <li>{@code dataPublisher().subscribe()} will result in a permanent subscription that
   *       continues to send data as long as the sensor is active, only signalling {@code
   *       onComplete} when the sensor is destroyed.
   *   <li>{@code dataPublisher().untilStop().subscribe()} will result in a subscription that
   *       signals {@code onComplete} the next time the sensor stops. This can only happen when the
   *       bound lifecycle also moves to {@code onStop}. This is useful for creating subscriptions
   *       that should end when the app is moved to the background.
   * </ul>
   *
   * <p>Note that after the current {@link LifecyclePublisher#untilStop()} stops, Subscribers will
   * need to call {@link LifecyclePublisher#untilStop} again and resubscribe to receive data when
   * the sensor restarts.
   */
  LifecyclePublisher<T> dataPublisher();

  /** Standard builder interface for MobileSensor instances. */
  public interface Builder<T> {

    @CanIgnoreReturnValue
    public Builder<T> setContext(Context context);

    /**
     * Sets the lifecycle to which this sensor will be bound.
     *
     * <p>For foreground use cases, this should be an Activity or Fragment. For background use
     * cases, this should be an {@link androidx.lifecycle.LifecycleService}.
     *
     * <p>The sensor will be allocated in {@code onCreate}, runs only between {@code onStart} and
     * {@code onStop}, and will be released in {@code onDestroy}. See {@link
     * MobileSensorV2#getLifecycle} for more details.
     */
    @CanIgnoreReturnValue
    public Builder<T> setBoundLifecycle(LifecycleOwner lifecycle);

    /**
     * Equivalent to calling both {@link #setContext} and {@link #setBoundLifecycle}. Allows this
     * sensors to run only while the activity is in the foreground.
     */
    @CanIgnoreReturnValue
    default Builder<T> setActivity(ComponentActivity activity) {
      return setContext(activity).setBoundLifecycle(activity);
    }

    /**
     * Equivalent to calling both {@link #setContext} and {@link #setBoundLifecycle}. Allows this
     * sensor to run only while the fragment is in the foreground. Should only be called after
     * {@link Fragment#onAttach}.
     */
    @CanIgnoreReturnValue
    default Builder<T> setFragment(Fragment fragment) {
      return setContext(fragment.requireContext()).setBoundLifecycle(fragment);
    }

    /**
     * Equivalent to calling both {@link #setContext} and {@link #setBoundLifecycle}. Allows this
     * sensor to run only while the service is started.
     */
    @CanIgnoreReturnValue
    default Builder<T> setService(LifecycleService service) {
      return setContext(service).setBoundLifecycle(service);
    }

    public MobileSensorV2<T> build();
  }
}
