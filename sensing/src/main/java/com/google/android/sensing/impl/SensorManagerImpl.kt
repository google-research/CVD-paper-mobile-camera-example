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
import com.google.android.sensing.inference.PostProcessor
import com.google.android.sensing.model.CaptureInfo
import com.google.android.sensing.model.CaptureType
import com.google.android.sensing.model.ProcessedInfo
import com.google.android.sensing.model.RequestStatus
import com.google.android.sensing.model.ResourceInfo
import com.google.android.sensing.model.SensorType
import java.io.File
import java.time.Instant
import java.util.Date
import java.util.UUID
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

internal class SensorManagerImpl(context: Context, private val database: Database) : SensorManager {
  private val sensorFactoryMap = mutableMapOf<SensorType, SensorFactory>()
  data class Components(
    val sensor: Sensor,
    var captureRequest: CaptureRequest? = null,
    var captureInfo: CaptureInfo? = null,
    var listener: SensorManager.AppDataCaptureListener? = null,
    var postProcessor: PostProcessor? = null
  )
  /**
   * This map is needed for faster lookup of capturing components enabling real time feedback to
   * application.
   */
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
      sensor.prepare(
        internalSensorListener =
          getSensorListener(context, lifecycleOwner.lifecycleScope.coroutineContext)
      )

      // For Active mode capturing (eg, Camera) we reset the sensor if user navigates to a different
      // screen / app
      if (sensor.getCaptureMode() == CaptureMode.ACTIVE) {
        lifecycleOwner.lifecycle.addObserver(
          object : DefaultLifecycleObserver {
            override fun onPause(owner: LifecycleOwner) {
              componentsMap[sensorType]?.let { runBlocking { reset(sensorType) } }
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
    applicationCoroutineContext: CoroutineContext,
  ): Sensor.InternalSensorListener {
    /**
     * In these events we can fairly assume for captureRequest to be non-null in the [componentsMap]
     * entry.
     */
    return object : Sensor.InternalSensorListener {
      override suspend fun onStarted(sensorType: SensorType) {
        componentsMap[sensorType]?.let { captureComponents ->
          captureComponents.captureRequest?.let { request ->
            /**
             * Real time feedback to application is more important than waiting for a likely
             * successful database transaction below.
             */
            val captureInfoToBeSaved =
              with(request) {
                CaptureInfo(
                  captureId = captureId,
                  externalIdentifier = externalIdentifier,
                  captureType = CaptureType.VIDEO_PPG,
                  captureFolder = File(context.filesDir, outputFolder).absolutePath,
                  captureTime = Date.from(Instant.now()),
                  captureSettings = CaptureSettings(emptyMap(), emptyMap(), "")
                )
              }
            withContext(applicationCoroutineContext) {
              captureComponents.listener?.onStart(captureInfoToBeSaved)
            }

            /**
             * Now offload DB tasks to IO pool of threads. New coroutine so that main thread remains
             * unblocked.
             */
            withContext(Dispatchers.IO) {
              try {
                database.addCaptureInfo(captureInfoToBeSaved)
                captureComponents.captureInfo = captureInfoToBeSaved
              } catch (e: ResourceNotFoundException) {
                Timber.e("SensorManagerImpl: Could not fetch CaptureInfo. Error: $e")
                withContext(applicationCoroutineContext) {
                  captureComponents.listener?.onError(e)
                  stop(sensorType)
                }
              }
            }
          }
        }
      }

      override suspend fun onData(sensorType: SensorType) {
        TODO("Not yet implemented")
      }

      override suspend fun onStopped(sensorType: SensorType) {
        componentsMap[sensorType]?.let { captureComponents ->
          captureComponents.captureRequest?.let { captureRequest ->
            // We can safely assume captureInfo is non-null here
            val captureInfo = captureComponents.captureInfo!!
            /** Remove captureRequest for new captures to start. */
            captureComponents.apply {
              this.captureRequest = null
              this.captureInfo = null
            }
            /**
             * Real time feedback to application is more important than waiting for a likely
             * successful database transaction below.
             */
            val resourceInfoToBeSaved =
              with(captureRequest) {
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
              }
            val newCaptureInfo = captureInfo.copy(resourceInfoList = listOf(resourceInfoToBeSaved))
            withContext(applicationCoroutineContext) {
              captureComponents.listener?.onStopped(newCaptureInfo)
            }
            try {
              captureComponents.postProcessor?.process(newCaptureInfo).let {
                withContext(applicationCoroutineContext) {
                  captureComponents.listener?.onPostProcessed(ProcessedInfo(it))
                }
              }
            } catch (e: Exception) {
              withContext(applicationCoroutineContext) {
                captureComponents.listener?.onError(e, newCaptureInfo)
                stop(sensorType)
              }
            }

            /** Now offload DB tasks to IO pool of threads. */
            withContext(Dispatchers.IO) {
              try {
                database.addResourceInfo(resourceInfoToBeSaved)
                database.getCaptureInfo(captureRequest.captureId).let {
                  withContext(applicationCoroutineContext) {
                    captureComponents.listener?.onRecordSaved(it)
                  }
                }
              } catch (e: ResourceNotFoundException) {
                Timber.e("SensorManagerImpl: Could not fetch updated CaptureInfo. Error: $e")
                withContext(applicationCoroutineContext) {
                  captureComponents.listener?.onError(e, newCaptureInfo)
                  stop(sensorType)
                }
              }
            }
          }
        }
      }

      override suspend fun onCancelled(sensorType: SensorType) {
        componentsMap[sensorType]?.let { captureComponents ->
          withContext(applicationCoroutineContext) {
            captureComponents.listener?.onCancelled(captureComponents.captureInfo)
          }
          captureComponents.captureInfo = null
        }
      }

      override suspend fun onError(sensorType: SensorType, exception: Exception) {
        stop(sensorType)
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
      componentsMap[sensorType]?.sensor?.start(captureRequest)
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
      if (componentsMap[sensorType]?.captureRequest == null) {
        Timber.w("Sensor $sensorType not started. Nothing to stop.")
        return
      }
      Timber.w("Stopping Sensor $sensorType.")
    }
    componentsMap[sensorType]?.sensor?.stop()
  }

  override suspend fun pause(sensorType: SensorType) {
    TODO("Not yet implemented")
  }

  override suspend fun resume(sensorType: SensorType) {
    TODO("Not yet implemented")
  }

  override suspend fun cancel(sensorType: SensorType) {
    val sensorTypeMutex = validate(sensorType)
    if (!componentsMap.containsKey(sensorType)) {
      Timber.w("Sensor $sensorType has not been initialized. ")
      return
    }
    if (componentsMap[sensorType]?.captureRequest == null) {
      Timber.w("Sensor $sensorType not started. Nothing to cancel.")
      return
    }
    componentsMap[sensorType]?.let {
      sensorTypeMutex.withLock { it.captureRequest = null }
      if (it.sensor.isStarted()) it.sensor.cancel()
    }
  }

  override suspend fun reset(sensorType: SensorType) {
    if (!componentsMap.containsKey(sensorType)) {
      Timber.w("Sensor $sensorType has not been initialized. ")
      return
    }
    val sensorTypeMutex = validate(sensorType)
    sensorTypeMutex
      .withLock { componentsMap.remove(sensorType) }
      ?.let {
        if (it.sensor.isStarted()) it.sensor.cancel()
        it.sensor.reset()
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
        "Sensor $sensorType has a pending capture request. Call to #registerListener should be prior to #start. Call #reset and start again from #init step."
      )
    }
    componentsMap[sensorType]?.let { it.listener = listener }
      ?: Timber.w("Cant register listener for Sensor $sensorType. Call #init first.")
  }

  override fun registerPostProcessor(sensorType: SensorType, postProcessor: PostProcessor) {
    if (!componentsMap.containsKey(sensorType)) {
      throw IllegalStateException("Sensor $sensorType not initialized. Call #init first.")
    }
    /**
     * If a captureRequest is already present then it means that [start] has already been called.
     */
    if (componentsMap[sensorType]?.captureRequest != null) {
      throw IllegalStateException(
        "Sensor $sensorType has a pending capture request. Call to #registerPostProcessor should be prior to #start. Call #reset and start again from #init step."
      )
    }
    componentsMap[sensorType]?.let { it.postProcessor = postProcessor }
      ?: Timber.w("Cant register post-processor for Sensor $sensorType. Call #init first.")
  }

  override fun unregisterPostProcessor(sensorType: SensorType) {
    if (!componentsMap.containsKey(sensorType)) {
      Timber.w("Sensor $sensorType not initialized. Call #init first.")
    }
    /**
     * If a captureRequest is already present then it means that [start] has already been called.
     */
    if (componentsMap[sensorType]?.captureRequest != null) {
      throw IllegalStateException(
        "Sensor $sensorType has a pending capture request. Call to unregisterPostProcessor should be prior to #start. Call #reset and start again from #init step."
      )
    }

    componentsMap[sensorType]?.postProcessor = null
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
   * Validates and fetches the appropriate [Mutex] object for the given `sensorType`. If necessary,
   * throws appropriate exceptions based on the state of the SensorManager.
   *
   * @param sensorType The type of sensor to validate.
   * @return The [Mutex] object associated with the provided `sensorType`.
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
