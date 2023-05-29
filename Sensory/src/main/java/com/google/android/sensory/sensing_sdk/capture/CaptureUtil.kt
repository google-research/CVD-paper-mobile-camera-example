/*
 * Copyright 2022 Google LLC
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

package com.google.android.sensory.sensing_sdk.capture

import androidx.camera.core.TorchState
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.fitbit.research.sensing.common.libraries.camera.Camera2InteropSensor

class CaptureUtil {
  companion object {
    fun toggleFlash(
      camera: Camera2InteropSensor,
      toggleFlash: FloatingActionButton,
    ) {
      val c = camera.cameraXSensor.camera
      if (c!!.cameraInfo.torchState.value == TorchState.ON) {
        // Turn off flash
        c.cameraControl.enableTorch(false)
        // toggleFlash.setImageResource(R.drawable.quantum_gm_ic_flashlight_off_vd_theme_24)
      } else {
        // Turn on flash
        c.cameraControl.enableTorch(true)
        // toggleFlash.setImageResource(R.drawable.quantum_gm_ic_flashlight_on_vd_theme_24)
      }
    }
  }
}
