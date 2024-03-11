/*
 * Copyright 2024 Google LLC
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

package com.google.android.sensing

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.google.android.sensing.capture.InitConfig
import com.google.android.sensing.capture.sensors.Sensor

/**
 * A factory interface responsible for creating `Sensor` instances of a specific type.
 * Implementations of this interface ensure compatibility between sensor implementations and the
 * `SensorManager`.
 */
interface SensorFactory {

  /**
   * Creates a new `Sensor` instance based on the provided configuration.
   *
   * @param context Android Context for accessing system resources.
   * @param lifecycleOwner A `LifecycleOwner` to manage the lifecycle of the created sensor.
   * @param initConfig Sensor-specific initialization configuration.
   * @return A new instance of a `Sensor` implementation compatible with the `SensorManager`.
   */
  fun create(context: Context, lifecycleOwner: LifecycleOwner, initConfig: InitConfig): Sensor
}
