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
import com.google.android.sensing.db.Database
import com.google.android.sensing.impl.SensorManagerImpl
import com.google.android.sensing.model.CaptureInfo
import com.google.android.sensing.model.SensorType

/**
 * Core interface responsible for managing the lifecycle and capture operations of various sensors.
 * Implementations of this interface provide a unified way to initialize, start, stop, and configure
 * the behavior of supported sensors.
 */
interface SensorManager {
  /**
   * Initializes a specific sensor for capture.
   * @param sensorType to initialize.
   * @param context in which the capture shall occur
   */
  suspend fun init(
    sensorType: SensorType,
    context: Context,
    lifecycleOwner: LifecycleOwner,
    initConfig: InitConfig
  )

  suspend fun start(sensorType: SensorType, captureRequest: CaptureRequest)
  suspend fun stop(sensorType: SensorType)
  suspend fun pause(sensorType: SensorType) // Optional, if applicable
  suspend fun resume(sensorType: SensorType) // Optional, if applicable

  interface AppDataCaptureListener {
    fun onStart(captureInfo: CaptureInfo)
    fun onComplete(captureInfo: CaptureInfo)
    fun onError(exception: Exception, captureInfo: CaptureInfo? = null)
  }
  fun registerListener(sensorType: SensorType, listener: AppDataCaptureListener)
  fun isStarted(sensorType: SensorType): Boolean
  fun getSensor(sensorType: SensorType): Any?
  fun getSupportedSensors(): List<SensorType>

  companion object {
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
