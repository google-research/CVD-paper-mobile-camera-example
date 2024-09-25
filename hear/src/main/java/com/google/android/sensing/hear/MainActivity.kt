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
import com.google.android.sensing.model.InternalSensorType
import kotlinx.coroutines.launch

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
    }
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
