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

package com.google.android.sensing.capture

import android.media.AudioFormat
import androidx.camera.core.CameraSelector
import androidx.camera.core.UseCase

/** Configurations required for initializing Sensor. */
open class InitConfig(open val captureMode: CaptureMode) {
  enum class CaptureMode {
    ACTIVE,
    PASSIVE
  }
  data class CameraInitConfig(
    val cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
    val useCases: List<UseCase> = emptyList()
  ) : InitConfig(CaptureMode.ACTIVE)

  data class MicrophoneInitConfig(
    val sampleRate: Int = 44100, // Sample rate in Hz (common choices: 44100, 48000)
    val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO, // Mono or Stereo
    val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT // 8-b
  ) : InitConfig(CaptureMode.ACTIVE)
}
