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

package com.google.android.sensing.capture.sensors

import com.google.android.sensing.capture.CaptureRequest
import com.google.android.sensing.model.SensorType

/**
 * Generic Sensor interface. Provides capture control for a particular implementation of a sensor.
 */
internal interface Sensor {

  interface SensorListener {
    fun onStarted(sensorType: SensorType, captureRequest: CaptureRequest)
    fun onData(sensorType: SensorType)
    fun onStopped(sensorType: SensorType)
    fun onError(sensorType: SensorType, exception: Exception)
  }
  suspend fun prepare(sensorListener: SensorListener)

  /** Is invoked within [CoroutineContext] [Dispatchers.IO] */
  suspend fun start(captureRequest: CaptureRequest)
  suspend fun stop()
  suspend fun pause()
  suspend fun resume()
  fun kill()

  fun getSensor(): Any
  fun isStarted(): Boolean
}
