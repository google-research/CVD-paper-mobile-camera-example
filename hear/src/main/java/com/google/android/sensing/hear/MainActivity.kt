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

package com.google.android.sensing.hear

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.sensing.SensorManager
import com.google.android.sensing.capture.sensors.MicrophoneInitConfig
import com.google.android.sensing.inference.PostProcessor
import com.google.android.sensing.model.CaptureInfo
import com.google.android.sensing.model.InternalSensorType
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

  private val mainActivityViewModel: MainActivityViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    lifecycleScope.launch {
      mainActivityViewModel.permissionsAvailable.collect {
        if (it) {
          setupSensorManager()
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    if (allPermissionsGranted()) mainActivityViewModel.setPermissionsAvailability(true)
    // else request for permissions is triggered in the InstructionFragment.
  }

  fun checkAllPermissions(): Boolean {
    if (allPermissionsGranted()) {
      return true
    } else {
      requestPermissions(requiredPermissions, REQUEST_CODE)
    }
    return false
  }

  private fun setupSensorManager() {
    // Init SensorManager for this application's use case - recording Audio
    val sensorManager = SensorManager.getInstance(applicationContext)
    lifecycleScope.launch {
      sensorManager.init(
        sensorType = InternalSensorType.MICROPHONE,
        this@MainActivity,
        this@MainActivity,
        MicrophoneInitConfig(
          sampleRate = 16000,
          channelConfig = AudioFormat.CHANNEL_IN_MONO,
          audioFormat = AudioFormat.ENCODING_PCM_16BIT
        )
      )
      sensorManager.registerPostProcessor(
        InternalSensorType.MICROPHONE,
        object : PostProcessor {
          override suspend fun process(captureInfo: CaptureInfo): String? {
            return HearApplication.getPredictionServiceClient(applicationContext)?.let {
              predictionServiceClient ->
              withContext(Dispatchers.IO) {
                getFirstOrNullImageUri(captureInfo.captureFolder, "wav")?.let {
                  mainActivityViewModel.predictWithAudio(
                    predictionServiceClient = predictionServiceClient,
                    endpointName = HearApplication.getEndpointName(applicationContext),
                    audioFile = it
                  )
                }
              }
            }
          }
        }
      )
    }
  }

  fun getFirstOrNullImageUri(path: String, fileType: String): File? {
    val file = File(path)
    return file.listFiles()?.firstOrNull { it.extension == fileType }
  }

  private fun allPermissionsGranted() =
    requiredPermissions.all {
      ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

  companion object {
    const val REQUEST_CODE = 0
    private val requiredPermissions = arrayOf(Manifest.permission.RECORD_AUDIO)
  }
}
