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

package com.google.android.sensing.testing

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.google.android.sensing.SensorFactory
import com.google.android.sensing.capture.CaptureMode
import com.google.android.sensing.capture.CaptureRequest
import com.google.android.sensing.capture.InitConfig
import com.google.android.sensing.capture.sensors.Sensor
import com.google.android.sensing.model.SensorType
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking

class TestSensor : Sensor {
  private lateinit var internalSensorListener: Sensor.InternalSensorListener
  private var isStarted = AtomicBoolean(false)

  override suspend fun prepare(internalSensorListener: Sensor.InternalSensorListener) {
    this.internalSensorListener = internalSensorListener
  }

  override suspend fun start(captureRequest: CaptureRequest) {
    isStarted.set(true)
    internalSensorListener.onStarted(TEST_SENSOR_TYPE)
  }

  override suspend fun stop() {
    internalSensorListener.onStopped(TEST_SENSOR_TYPE)
    isStarted.set(false)
  }

  override suspend fun pause() {
    TODO("Not yet implemented")
  }

  override suspend fun resume() {
    TODO("Not yet implemented")
  }

  override fun kill() {
    runBlocking { stop() }
  }

  override fun getSensor() = Any()

  override fun isStarted() = isStarted.get()

  override fun getCaptureMode() = CaptureMode.ACTIVE
}

object TEST_SENSOR_TYPE : SensorType

object TEST_SENSOR_FACTORY : SensorFactory {
  override fun create(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    initConfig: InitConfig,
  ) = TestSensor()
}

data class TestSensorInitConfig(
  val config: String = "",
) : InitConfig(CaptureMode.ACTIVE)

data class TestSensorCaptureRequest(
  override val externalIdentifier: String = TEST_EXTERNAL_ID,
  override val outputFolder: String = TEST_OUTPUT_FOLDER,
  override val outputTitle: String = TEST_OUTPUT_TITLE,
) :
  CaptureRequest(
    externalIdentifier = externalIdentifier,
    outputFolder = outputFolder,
    outputFormat = OUTPUT_FORMAT,
    outputTitle = outputTitle
  )

const val TEST_EXTERNAL_ID = "TEST_EXTERNAL_ID"
const val TEST_OUTPUT_FOLDER = "TEST_OUTPUT_FOLDER"
const val TEST_OUTPUT_TITLE = "TEST_OUTPUT_TITLE"
const val OUTPUT_FORMAT = "jpg"
