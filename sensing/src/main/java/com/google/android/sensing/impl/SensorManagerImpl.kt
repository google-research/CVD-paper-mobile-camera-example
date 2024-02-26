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
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.sensing.SensorManager
import com.google.android.sensing.capture.CameraCaptureRequest
import com.google.android.sensing.capture.CaptureRequest
import com.google.android.sensing.capture.CaptureSettings
import com.google.android.sensing.capture.InitConfig
import com.google.android.sensing.capture.sensors.CameraSensor
import com.google.android.sensing.capture.sensors.Sensor
import com.google.android.sensing.db.Database
import com.google.android.sensing.db.ResourceNotFoundException
import com.google.android.sensing.model.CaptureInfo
import com.google.android.sensing.model.CaptureType
import com.google.android.sensing.model.RequestStatus
import com.google.android.sensing.model.ResourceInfo
import com.google.android.sensing.model.SensorType
import java.io.File
import java.lang.IllegalStateException
import java.time.Instant
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

internal class SensorManagerImpl(context: Context, private val database: Database) : SensorManager {
  data class Components(
    val captureId: String,
    val sensor: Sensor,
    var captureRequest: CaptureRequest? = null,
    var listener: SensorManager.AppDataCaptureListener? = null,
  )
  private val componentsMap = mutableMapOf<SensorType, Components>()

  override suspend fun init(
    sensorType: SensorType,
    context: Context,
    lifecycleOwner: LifecycleOwner,
    initConfig: InitConfig
  ) {
    componentsMap.remove(sensorType)
    val sensor =
      when (sensorType) {
        SensorType.CAMERA ->
          CameraSensor(context, lifecycleOwner, initConfig as InitConfig.CameraInitConfig)
        else -> TODO()
      }
    val sensorListener =
      object : Sensor.SensorListener {
        override fun onStarted(sensorType: SensorType, captureRequest: CaptureRequest) {
          // Offload DB tasks to IO pool of threads.
          CoroutineScope(Dispatchers.IO).launch {
            componentsMap[sensorType]?.let {
              with(captureRequest) {
                it.captureRequest = this
                database.addCaptureInfo(
                  CaptureInfo(
                    captureId = it.captureId,
                    externalIdentifier = externalIdentifier,
                    captureType = CaptureType.VIDEO_PPG,
                    captureFolder = File(context.filesDir, outputFolder).absolutePath,
                    captureTime = Date.from(Instant.now()),
                    captureSettings = CaptureSettings(emptyMap(), emptyMap(), "")
                  )
                )
              }
              try {
                database.getCaptureInfo(it.captureId).let { captureInfo ->
                  // Back to original coroutine context. For application this will generally run in
                  // the main thread.
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

        override fun onData(sensorType: SensorType) {}

        override fun onStopped(sensorType: SensorType) {
          componentsMap.remove(sensorType)?.let {
            CoroutineScope(Dispatchers.IO).launch {
              it.captureRequest?.let { request ->
                with(request) {
                  database.addResourceInfo(
                    ResourceInfo(
                      resourceInfoId = UUID.randomUUID().toString(),
                      captureId = it.captureId,
                      externalIdentifier = externalIdentifier,
                      resourceTitle = outputTitle,
                      contentType = outputFormat,
                      localLocation = File(context.filesDir, outputFolder).absolutePath,
                      remoteLocation = "",
                      /** TODO */
                      status = RequestStatus.PENDING
                    )
                  )
                }
                try {
                  database.getCaptureInfo(it.captureId).let { captureInfo ->
                    // Back to original coroutine context. For application this will generally run
                    // in the main thread.
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
              /** TODO zipping and creating UploadRequest */
            }
          }
        }

        override fun onError(sensorType: SensorType, exception: Exception) {
          CoroutineScope(Dispatchers.IO).launch { stop(sensorType) }
        }
      }
    sensor.prepare(sensorListener)
    assert(!componentsMap.contains(sensorType))
    // Assign a captureId to the current capture.
    componentsMap[sensorType] = Components(UUID.randomUUID().toString(), sensor)
  }

  override suspend fun start(sensorType: SensorType, captureRequest: CaptureRequest) {
    if (!componentsMap.containsKey(sensorType))
      throw IllegalStateException("Sensor $sensorType not ready. Call #init(<SensorType>) first")
    if (componentsMap[sensorType]?.captureRequest != null) {
      throw IllegalStateException("Sensor $sensorType already started. Call #init to reset.")
    }
    if (!isCompatible(sensorType, captureRequest)) {
      throw IllegalStateException(
        "Sensor $sensorType and CaptureRequest $captureRequest not compatible."
      )
    }
    componentsMap[sensorType]?.let {
      try {
        withContext(Dispatchers.IO) { it.sensor.start(captureRequest) }
      } catch (e: IllegalStateException) {
        Timber.w(e.message)
      }
    }
  }

  private fun isCompatible(sensorType: SensorType, captureRequest: CaptureRequest): Boolean {
    return when (sensorType) {
      SensorType.CAMERA -> captureRequest is CameraCaptureRequest
      else -> TODO()
    }
  }

  override suspend fun stop(sensorType: SensorType) {
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

  override suspend fun pause(sensorType: SensorType) {
    TODO("Not yet implemented")
  }

  override suspend fun resume(sensorType: SensorType) {
    TODO("Not yet implemented")
  }

  override fun registerListener(
    sensorType: SensorType,
    listener: SensorManager.AppDataCaptureListener
  ) {
    componentsMap[sensorType]?.let { it.listener = listener }
      ?: Timber.w("Cant register listener for Sensor $sensorType. Call #init and #start first.")
  }

  override fun isStarted(sensorType: SensorType): Boolean {
    return componentsMap[sensorType]?.sensor?.isStarted()
      ?: run {
        Timber.w("Cant register PostProcessor for Sensor $sensorType. Call #init and #start first.")
        false
      }
  }

  override fun getSensor(sensorType: SensorType) = componentsMap[sensorType]?.sensor?.getSensor()

  override fun getSupportedSensors() = listOf(SensorType.CAMERA)
}
