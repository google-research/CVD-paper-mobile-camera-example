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
import com.google.android.sensing.capture.sensors.CameraSensorFactor
import com.google.android.sensing.capture.sensors.MicrophoneSensorFactory
import com.google.android.sensing.db.Database
import com.google.android.sensing.impl.SensorManagerImpl
import com.google.android.sensing.model.CaptureInfo
import com.google.android.sensing.model.InternalSensorType
import com.google.android.sensing.model.SensorType

/**
 * Core interface for managing various sensors, with a focus on AI-powered analysis. The
 * SensorManager supports intelligent applications like remote patient monitoring. Key features
 * include:
 *
 * * Unified Application Interface: Simplifies app-sensor interaction, masking complexities of
 * sensor management.
 * * Dynamic Sensor Registration: Enables flexible addition of new sensor types.
 * * Sensor Lifecycle Control: Manages sensor states (prepare, start, pause, stop) for smooth
 * operation.
 * * Data Handling: Processes raw sensor data, prepares it for storage and transmission.
 * * TODO Post-Processing Support: Hooks for data analysis and potential on-device AI inference.
 * * Robust Error Handling: Catches sensor errors, provides informative feedback.
 *
 * Important Note:
 * * Individual `Sensor` implementations may still perform basic state validations to ensure their
 * internal integrity, even if the `SensorManager` enforces the overall state machine.
 */
interface SensorManager {

  /**
   * Registers a `SensorFactory` for the specified `sensorType`. This factory will be responsible
   * for creating sensor instances of the given type when the `init` method is called.
   *
   * @param sensorType The unique type of sensor to register a factory for.
   * @param sensorFactory The `SensorFactory` implementation responsible for creating sensors of the
   * specified `sensorType`.
   * @throws IllegalArgumentException if a factory is already registered for the given `sensorType`.
   */
  fun registerSensorFactory(sensorType: SensorType, sensorFactory: SensorFactory)

  /**
   * Unregisters the `SensorFactory` associated with the specified `sensorType`. After
   * unregistering, the `SensorManager` will no longer be able to create sensors of this type.
   *
   * @param sensorType The type of sensor whose factory should be unregistered.
   */
  fun unregisterSensorFactory(sensorType: SensorType)

  /**
   * Checks whether a `SensorFactory` is currently registered for the specified `sensorType`.
   *
   * @param sensorType The type of sensor to check for registration.
   * @return `true` if a factory is registered for the `sensorType`, `false` otherwise.
   */
  fun checkRegistration(sensorType: SensorType): Boolean

  /**
   * Initializes a specified sensor in preparation for capturing data. This involves:
   * 1. Validating that a SensorFactory is registered for the sensorType
   * 2. Acquiring a lock (Mutex) to ensure thread safety for this sensor
   * 3. Fetching or creating the Sensor instance using the SensorFactory
   * 4. Preparing the sensor, setting up lifecycle observers, etc.
   *
   * @param sensorType The type of sensor to initialize (e.g., SensorType.CAMERA,
   * SensorType.MICROPHONE)
   * @param context Android Context for accessing system resources.
   * @param lifecycleOwner A LifecycleOwner (typically Activity or Fragment) to tie the sensor's
   * lifecycle. [CoroutineContext] from [lifecycleOwner.lifecycleScop.coroutineContext] is used to
   * invoke all application callbacks.
   * @param initConfig Sensor-specific initialization configuration. Example [CameraInitConfig].
   * @throws IllegalStateException when:
   * 1. [SensorFactory] is not registered for [SensorType]
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
   * 2. [start] is called before [stop]-ing the previous capture,
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
   * Cancel the ongoing capturing. However, you can still use the [Sensor] instance without doing an
   * [init] again.
   *
   * @param sensorType The type of sensor to cancel.
   */
  suspend fun cancel(sensorType: SensorType)

  /**
   * Resets SensorManager for the specified [sensorType] by invoking [Sensor].reset. Call it after
   * [stop]. If called before [stop] capturing is [cancel]ed. Post this, the same sensor instance
   * will not be available and to capture with [sensorType] you would need to [init] the sensor type
   * again.
   *
   * @param sensorType The type of sensor to reset.
   */
  suspend fun reset(sensorType: SensorType)

  /**
   * Interface for receiving notifications about sensor capture events and results. These events are
   * invoked within [CoroutineContext] provided by application.
   */
  interface AppDataCaptureListener {
    fun onStart(captureInfo: CaptureInfo)
    fun onStopped(captureInfo: CaptureInfo)
    fun onCancelled(captureInfo: CaptureInfo?)
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
                  SensorManagerImpl(
                      context,
                      database,
                      sensingEngineConfiguration.serverConfiguration
                    )
                    .apply {
                      registerSensorFactory(InternalSensorType.CAMERA, CameraSensorFactor)
                      registerSensorFactory(InternalSensorType.MICROPHONE, MicrophoneSensorFactory)
                    }
                }
              }
              .also { instance = it }
        }
  }
}
