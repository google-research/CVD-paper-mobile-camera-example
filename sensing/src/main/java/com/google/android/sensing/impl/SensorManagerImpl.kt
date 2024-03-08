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

package com.google.android.sensing.impl

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.sensing.SensorFactory
import com.google.android.sensing.SensorManager
import com.google.android.sensing.capture.CaptureMode
import com.google.android.sensing.capture.CaptureRequest
import com.google.android.sensing.capture.CaptureSettings
import com.google.android.sensing.capture.InitConfig
import com.google.android.sensing.capture.sensors.Sensor
import com.google.android.sensing.db.Database
import com.google.android.sensing.db.ResourceNotFoundException
import com.google.android.sensing.model.CaptureInfo
import com.google.android.sensing.model.CaptureType
import com.google.android.sensing.model.RequestStatus
import com.google.android.sensing.model.ResourceInfo
import com.google.android.sensing.model.SensorType
import java.io.File
import java.time.Instant
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

internal class SensorManagerImpl(context: Context, private val database: Database) : SensorManager {
  private val sensorFactoryMap = mutableMapOf<SensorType, SensorFactory>()
  data class Components(
    val sensor: Sensor,
    var captureRequest: CaptureRequest? = null,
    var listener: SensorManager.AppDataCaptureListener? = null,
  )
  private val componentsMap = mutableMapOf<SensorType, Components>()

  private val mutexMap = mutableMapOf<SensorType, Mutex>()

  override fun registerSensorFactory(sensorType: SensorType, sensorFactory: SensorFactory) {
    if (sensorFactoryMap.containsKey(sensorType)) {
      throw IllegalStateException(
        "SensorFactor already registered for SensorType $sensorType. Use #unregisterSensorFactory maybe!"
      )
    }
    sensorFactoryMap[sensorType] = sensorFactory
    mutexMap[sensorType] = Mutex()
  }

  override fun unregisterSensorFactory(sensorType: SensorType) {
    sensorFactoryMap.remove(sensorType)
    mutexMap.remove(sensorType)
  }

  override fun checkRegistration(sensorType: SensorType) = sensorFactoryMap.containsKey(sensorType)

  override suspend fun init(
    sensorType: SensorType,
    context: Context,
    lifecycleOwner: LifecycleOwner,
    initConfig: InitConfig
  ) {
    val sensorTypeMutex = validate(sensorType)
    sensorTypeMutex.withLock {
      if (componentsMap.containsKey(sensorType)) {
        throw IllegalStateException("Sensor $sensorType already initialized. Call #reset first.")
      }
      /** Within [sensorTypeMutex] we can assume SensorFactory will be non null. */

      // Fetch SensorFactory and create Sensor instance.
      val sensor = sensorFactoryMap[sensorType]!!.create(context, lifecycleOwner, initConfig)

      // Prepare sensor for capture
      sensor.prepare(internalSensorListener = getSensorListener(context, lifecycleOwner))

      // For Active mode capturing (eg, Camera) we reset the sensor if user navigates to a different
      // screen / app
      if (sensor.getCaptureMode() == CaptureMode.ACTIVE) {
        lifecycleOwner.lifecycle.addObserver(
          object : DefaultLifecycleObserver {
            override fun onPause(owner: LifecycleOwner) {
              reset(sensorType)
            }
          }
        )
      }

      // Create an entry in the Component map.
      componentsMap[sensorType] = Components(sensor = sensor)
    }
  }

  private fun getSensorListener(
    context: Context,
    lifecycleOwner: LifecycleOwner
  ): Sensor.InternalSensorListener {
    /**
     * In these events we can fairly assume for captureRequest to be non-null in the [componentsMap]
     * entry.
     */
    return object : Sensor.InternalSensorListener {
      override fun onStarted(sensorType: SensorType) {
        // Offload DB tasks to IO pool of threads.
        CoroutineScope(Dispatchers.IO).launch {
          componentsMap[sensorType]?.let {
            it.captureRequest?.let { request ->
              with(request) {
                database.addCaptureInfo(
                  CaptureInfo(
                    captureId = this.captureId,
                    externalIdentifier = externalIdentifier,
                    captureType = CaptureType.VIDEO_PPG,
                    captureFolder = File(context.filesDir, outputFolder).absolutePath,
                    captureTime = Date.from(Instant.now()),
                    captureSettings = CaptureSettings(emptyMap(), emptyMap(), "")
                  )
                )
                try {
                  database.getCaptureInfo(this.captureId).let { captureInfo ->
                    /**
                     * Back to original coroutine context. For application this will generally run
                     * in the main thread.
                     */
                    withContext(lifecycleOwner.lifecycleScope.coroutineContext) {
                      it.listener?.onStart(captureInfo)
                    }
                  }
                } catch (e: ResourceNotFoundException) {
                  it.listener?.onError(e)
                  stop(sensorType)
                }
              }
            }
          }
        }
      }

      override fun onData(sensorType: SensorType) {}

      override fun onStopped(sensorType: SensorType) {
        componentsMap.remove(sensorType)?.let {
          CoroutineScope(Dispatchers.IO).launch {
            it.captureRequest?.let { request ->
              with(request) {
                database.addResourceInfo(
                  ResourceInfo(
                    resourceInfoId = UUID.randomUUID().toString(),
                    captureId = this.captureId,
                    externalIdentifier = externalIdentifier,
                    resourceTitle = outputTitle,
                    contentType = outputFormat,
                    localLocation = File(context.filesDir, outputFolder).absolutePath,
                    /** TODO Update remoteLocation for Sensing1.0. */
                    remoteLocation = "",
                    status = RequestStatus.PENDING
                  )
                )
                try {
                  database.getCaptureInfo(this.captureId).let { captureInfo ->
                  /**
                   * Back to original coroutine context. For application this will generally run in
                   * the main thread.
                   */
                  val processedString =
                      withContext(lifecycleOwner.lifecycleScope.coroutineContext) {
                        it.listener?.onComplete(captureInfo)
                        /** TODO PostProcess. */
                      }
                  }
                } catch (e: ResourceNotFoundException) {
                  it.listener?.onError(e)
                  stop(sensorType)
                }
              }
            }
            /** TODO zipping and creating UploadRequest */
          }
        }
      }

      override fun onError(sensorType: SensorType, exception: Exception) {
        CoroutineScope(Dispatchers.IO).launch { stop(sensorType) }
      }
    }
  }

  override suspend fun start(sensorType: SensorType, captureRequest: CaptureRequest) {
    val sensorTypeMutex = validate(sensorType)
    sensorTypeMutex.withLock {
      if (!componentsMap.containsKey(sensorType))
        throw IllegalStateException(
          "Sensor $sensorType not initialized. Possible view lifecycle pause event triggered reset. Call #init(<SensorType>) first"
        )
      // If a captureRequest is already present then it means that either capturing is happening or
      // has stopped but reset has not been called.
      if (componentsMap[sensorType]?.captureRequest != null) {
        throw IllegalStateException(
          "Sensor $sensorType has a pending capture request $captureRequest. Call #stop to stop capturing or #reset to reset."
        )
      }
      componentsMap[sensorType]?.captureRequest = captureRequest
    }
    try {
      withContext(Dispatchers.IO) { componentsMap[sensorType]?.sensor?.start(captureRequest) }
    } catch (e: IllegalStateException) {
      Timber.w(e.message)
    }
  }

  override suspend fun stop(sensorType: SensorType) {
    val sensorTypeMutex = validate(sensorType)
    sensorTypeMutex.withLock {
      if (!componentsMap.containsKey(sensorType)) {
        Timber.w("Sensor $sensorType not initialized. Nothing to stop.")
        return
      }
      if (componentsMap[sensorType]?.sensor == null) {
        Timber.w("Sensor $sensorType not started. Nothing to stop.")
        return
      }
      componentsMap[sensorType]?.sensor?.stop()
    }
  }

  override suspend fun pause(sensorType: SensorType) {
    TODO("Not yet implemented")
  }

  override suspend fun resume(sensorType: SensorType) {
    TODO("Not yet implemented")
  }

  override fun reset(sensorType: SensorType) {
    if (!componentsMap.containsKey(sensorType)) {
      Timber.w("Sensor $sensorType has not been initialized. ")
      return
    }
    if (componentsMap[sensorType]?.sensor?.isStarted() == true) {
      componentsMap[sensorType]?.sensor?.kill()
    }
  }

  override fun registerListener(
    sensorType: SensorType,
    listener: SensorManager.AppDataCaptureListener
  ) {
    if (!componentsMap.containsKey(sensorType)) {
      throw IllegalStateException("Sensor $sensorType not initialized. Call #init first.")
    }
    /**
     * If a captureRequest is already present then it means that [start] has already been called.
     */
    if (componentsMap[sensorType]?.captureRequest != null) {
      throw IllegalStateException(
        "Sensor $sensorType has a pending capture request. Call to this should be prior to #start. Call #reset and start again from #init step."
      )
    }
    componentsMap[sensorType]?.let { it.listener = listener }
      ?: Timber.w("Cant register listener for Sensor $sensorType. Call #init first.")
  }

  override fun isStarted(sensorType: SensorType): Boolean {
    return componentsMap[sensorType]?.sensor?.isStarted()
      ?: run {
        Timber.w("Sensor $sensorType not initialized. Call #init first.")
        false
      }
  }

  override fun getSensor(sensorType: SensorType) = componentsMap[sensorType]?.sensor?.getSensor()

  override fun getSupportedSensors() = sensorFactoryMap.keys.toList()

  /**
   * Fetches the appropriate SensorFactory, Sensor, and Mutex objects for the given `sensorType`. If
   * necessary, throws appropriate exceptions based on the state of the SensorManager.
   *
   * @param sensorType The type of sensor to validate.
   * @return The Mutex object associated with the provided `sensorType`.
   * @throws IllegalStateException if a factory is not registered for the `sensorType`, or if the
   * SensorManager is in an invalid state (e.g., trying to `start` an uninitialized sensor).
   */
  @Synchronized
  private fun validate(sensorType: SensorType): Mutex {
    if (!checkRegistration(sensorType)) {
      throw IllegalStateException(
        "No SensorFactory implementation has been registered for SensorType $sensorType. Register via #registerSensorFactory"
      )
    }
    return mutexMap[sensorType]!!
  }
}
