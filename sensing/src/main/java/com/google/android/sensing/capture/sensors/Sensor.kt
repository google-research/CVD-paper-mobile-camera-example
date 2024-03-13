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

import com.google.android.sensing.capture.CaptureMode
import com.google.android.sensing.capture.CaptureRequest
import com.google.android.sensing.model.SensorType

/**
 * Core interface defining the key operations and lifecycle management of an individual sensor. This
 * interface acts as a contract for different sensor implementations, providing a consistent way to
 * control their behavior.
 *
 * **Examples of sensors:**
 * * **Internal sensors:**
 * ```
 *     * Camera
 *     * Microphone
 *     * Accelerometer
 *     * Gyroscope
 * ```
 * * **External sensors:**
 * ```
 *     * Blood pressure monitors
 *     * Pulse oximeters
 *     * Continuous glucose monitors (CGM)
 *     * Electrocardiogram (ECG/EKG) sensors
 * ```
 * **Sensor Lifecycle:** A typical sensor lifecycle follows these stages:
 * 1. **Initialization:** A new `Sensor` instance (e.g., `CameraSensor`) is created.
 * 2. **Preparation (`Sensor.prepare`)**: The sensor is prepared for data capture, which may involve
 * acquiring resources, establishing connections, and configuring parameters.
 * 3. **Capture Start (`Sensor.start`)**: The sensor begins actively collecting data.
 * 4. **Capturing (Data Events):** The sensor may periodically trigger `onData` events in its
 * `SensorListener`.
 * 5. **Capture Stop (`Sensor.stop`)**: The active data capture process is halted.
 * 6. **Pause (`Sensor.pause` - Optional):** Capture is temporarily suspended.
 * 7. **Resume (`Sensor.resume` - Optional):** Capture continues from the paused state.
 * 8. **Kill (`Sensor.kill`):** Immediate, forceful termination of the capture process, releasing
 * resources.
 *
 * **Re-usability:** Sensors are generally reusable. After a capture session is stopped (`stop` or
 * `kill`), you should be able to `prepare` the sensor again for a new capture. Specific sensor
 * implementations might document exceptions or limitations if they exist.
 *
 * **Illegal States:** Sensors are not expected to enter illegal states as SensorManager validates
 * the capture operation calls. We also do not expect the Sensor implementations to be used
 * independently. Individual `Sensor` implementations may still perform basic state validations to
 * ensure their internal integrity:-
 * * Calling `start` before `prepare`.
 * * Calling `start` multiple times without an intervening `stop`.
 * * Sensor-specific restrictions on operations during capture. Refer to the documentation of the
 * specific sensor subclass for details.
 */
interface Sensor {

  /**
   * Internal interface for receiving notifications about lifecycle events and errors from
   * individual Sensor implementations. This interface is used by the SensorManagerImpl to manage
   * the state of active sensors.
   *
   * The inclusion of [SensorType] in each API method is necessary to support multiple sensor types
   * within the SensorManager (e.g., camera, microphone, etc.). It allows the SensorManager to
   * distinguish which specific sensor triggered an event, enabling accurate tracking and
   * management.
   */
  interface InternalSensorListener {
    fun onStarted(sensorType: SensorType)
    fun onData(sensorType: SensorType)
    fun onStopped(sensorType: SensorType)
    fun onError(sensorType: SensorType, exception: Exception)
  }

  /**
   * **[Async] Prepares the sensor for data capture.** This might involve acquiring resources,
   * establishing connections, or performing necessary setup. Requires a SensorListener to send
   * updates about the sensor's status to the [SensorManager].
   *
   * @param internalSensorListener The SensorListener to be notified about capture events.
   */
  suspend fun prepare(internalSensorListener: InternalSensorListener)

  /**
   * **[Async] Starts the sensor's data capture process.** This initiates the active acquisition of
   * sensor data, which will be reported to the SensorListener. Called within a coroutine using
   * Dispatchers.IO for offloading the work to an I/O thread.
   *
   * @param captureRequest Configuration details for the capture process (e.g., resolution, sample
   * rate).
   */
  suspend fun start(captureRequest: CaptureRequest)

  /** **[Async] Stops the sensor's data capture process.** Halts the active data collection. */
  suspend fun stop()

  suspend fun reset()

  /**
   * **[Async] Temporarily pauses the sensor's data capture process.** Allows for resuming data
   * collection without a full stop/start cycle.
   */
  suspend fun pause()

  /** **[Async] Resumes the data capture process for a previously paused sensor.** */
  suspend fun resume()

  /**
   * Immediately aborts the sensor's capture process and releases any held resources.** This is a
   * more forceful way to stop the sensor compared to the regular 'stop' function.
   */
  fun kill()

  /**
   * Retrieves the underlying platform-specific sensor object. Use with caution, as direct
   * manipulation of this object might break away from the abstraction provided by the `Sensor`
   * interface.
   *
   * @return The low-level sensor object, or possibly null if not applicable.
   */
  fun getSensor(): Any

  /**
   * Indicates whether the sensor is currently actively capturing data.
   *
   * @return True if the sensor is in the "started" state, false otherwise.
   */
  fun isStarted(): Boolean

  fun getCaptureMode(): CaptureMode
}
