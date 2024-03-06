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
import com.google.android.sensing.capture.CaptureRequest
import com.google.android.sensing.capture.InitConfig
import com.google.android.sensing.capture.sensors.CameraCaptureRequest
import com.google.android.sensing.capture.sensors.CameraInitConfig
import com.google.android.sensing.db.Database
import com.google.android.sensing.impl.SensorManagerImpl
import com.google.android.sensing.model.CaptureInfo
import com.google.android.sensing.model.SensorType

/**
 * Core interface responsible for managing the lifecycle and capture operations of various sensors.
 * Implementations of this interface provide a unified way to initialize, start, stop, and configure
 * the behavior of supported sensors.
 *
 * **Key Responsibilities:**
 * * **Sensor Lifecycle Management:** Ensures sensors transition through valid state changes
 * (prepare -> start -> pause/resume -> stop -> reset). This includes preventing invalid operations
 * (e.g., starting a sensor that hasn't been prepared).
 *
 * * **Error Handling:** Catches and manages errors arising from individual sensors, providing
 * meaningful feedback to the application layer.
 * * **Application Interface:** Provides a consistent API for the application to interact with
 * sensors, hiding the complexities of managing individual sensor states and potential errors.
 *
 * * **Data Processing and Persistence:** Handles events from sensor implementations, potentially
 * transforming raw sensor data, generating database records (e.g., CaptureInfo, ResourceInfo,
 * UploadRequest), and performing data preparation tasks like zipping for efficient transmission.
 *
 * Important Notes:
 * * Individual `Sensor` implementations may still perform basic state validations to ensure their
 * internal integrity, even if the `SensorManager` enforces the overall state machine.
 *
 * * Applications using this interface should generally not interact with `Sensor` objects directly,
 * relying on the `SensorManager` to orchestrate sensor operations.
 */
interface SensorManager {
  /**
   * Initializes a specified sensor in preparation for capturing data.
   *
   * @param sensorType The type of sensor to initialize (e.g., SensorType.CAMERA,
   * SensorType.MICROPHONE)
   * @param context Android Context for accessing system resources.
   * @param lifecycleOwner A LifecycleOwner (typically Activity or Fragment) to tie the sensor's
   * lifecycle.
   * @param initConfig Sensor-specific initialization configuration. Example [CameraInitConfig].
   * @throws IllegalStateException when
   * 1. [sensorType] is not compatible with [initConfig]
   * 2. [init] is called before [reset]-ting the previous capture
   */
  suspend fun init(
    sensorType: SensorType,
    context: Context,
    lifecycleOwner: LifecycleOwner,
    initConfig: InitConfig
  )

  /**
   * Starts the capture process for the specified sensor. Call after [init].
   *
   * @param sensorType The type of sensor to start.
   * @param captureRequest Details about the capture requirements (e.g., resolution, frame rate).
   * Example [CameraCaptureRequest].
   * @throws IllegalStateException when
   * 1. [start] is called before [init],
   * 2. [start] is called before [reset]-ting the previous capture,
   * 3. [sensorType] is not compatible with the given [captureRequest]
   */
  suspend fun start(sensorType: SensorType, captureRequest: CaptureRequest)

  /**
   * Stops the capture process for the specified sensor.
   *
   * @param sensorType The type of sensor to stop.
   */
  suspend fun stop(sensorType: SensorType)

  /**
   * **[Optional]** Temporarily pauses the capture process for the specified sensor.
   *
   * @param sensorType The type of sensor to pause.
   */
  suspend fun pause(sensorType: SensorType)

  /**
   * **[Optional]** Resumes the capture process for a previously paused sensor.
   *
   * @param sensorType The type of sensor to resume.
   */
  suspend fun resume(sensorType: SensorType)

  /**
   * Resets SensorManager for the specified sensor, killing the sensor if its capturing and
   * releasing any acquired resources. Call it after [stop]. If called before [stop] capturing is
   * [kill]ed.
   *
   * @param sensorType The type of sensor to reset.
   */
  fun reset(sensorType: SensorType)

  /** Interface for receiving notifications about sensor capture events and results. */
  interface AppDataCaptureListener {
    fun onStart(captureInfo: CaptureInfo)
    fun onComplete(captureInfo: CaptureInfo)
    fun onError(exception: Exception, captureInfo: CaptureInfo? = null)
  }

  /**
   * Registers a listener to receive capture-related events for a specific sensor.
   *
   * @param sensorType The type of sensor for which to receive events.
   * @param listener The AppDataCaptureListener implementation to handle events.
   * @throws IllegalStateException when
   * 1. [registerListener] is called before [init]
   * 2. [registerListener] is called after [start]
   */
  fun registerListener(sensorType: SensorType, listener: AppDataCaptureListener)

  /**
   * Checks whether a specified sensor is currently in the capturing state.
   *
   * @param sensorType The type of sensor to check.
   * @return True if the sensor is actively capturing data, false otherwise.
   */
  fun isStarted(sensorType: SensorType): Boolean

  /**
   * Retrieves the underlying sensor object for a given sensor type. Note: Exercise caution when
   * using this; direct sensor manipulation might be better encapsulated within the SensorManager.
   *
   * @param sensorType The type of sensor to retrieve.
   * @return The sensor object, or null if not found or not applicable.
   */
  fun getSensor(sensorType: SensorType): Any?

  /**
   * Returns a list of sensor types supported by the SensorManager implementation.
   *
   * @return A list of [SensorType] objects representing the supported sensors.
   */
  fun getSupportedSensors(): List<SensorType>

  companion object {
    // singleton instance
    @Volatile private var instance: SensorManager? = null
    fun getInstance(context: Context) =
      instance
        ?: synchronized(this) {
          instance
            ?: run {
              val appContext = context.applicationContext
              val sensingEngineConfiguration =
                if (appContext is SensingEngineConfiguration.Provider) {
                  appContext.getSensingEngineConfiguration()
                } else SensingEngineConfiguration()
              with(sensingEngineConfiguration) {
                val database = Database.getInstance(context, databaseConfiguration)
                SensorManagerImpl(context, database).also { instance = it }
              }
            }
        }
  }
}
