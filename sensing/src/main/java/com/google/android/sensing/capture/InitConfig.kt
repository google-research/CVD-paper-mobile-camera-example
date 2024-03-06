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

package com.google.android.sensing.capture

/**
 * Base class for sensor initialization configurations. Subclasses are used to provide
 * sensor-specific settings during the initialization step within the [SensorManager].
 *
 * @param captureMode The desired capture mode for the sensor. See [CaptureMode] for details.
 */
open class InitConfig(open val captureMode: CaptureMode) {
  /**
   * Enumerates supported capture modes for a sensor. The terminology here relates to the technical
   * behavior of the sensor, but the impact on the user experience can differ.
   * * ACTIVE: Sensors that run in foreground and require user intervention, like button clicking.
   * * PASSIVE: Sensors that run in background.
   */
  enum class CaptureMode {
    ACTIVE,
    PASSIVE
  }
}
