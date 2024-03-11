/*
 * Copyright 2023-2024 Google LLC
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

import android.content.Context
import android.content.res.ColorStateList
import androidx.camera.core.TorchState
import androidx.core.content.ContextCompat
import com.google.android.fitbit.research.sensing.common.libraries.camera.Camera2InteropSensor
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.sensing.R
import com.google.android.sensing.model.CaptureType
import com.google.android.sensing.model.InternalSensorType

class CaptureUtil {
  companion object {
    fun toggleFlashWithView(
      camera: Camera2InteropSensor,
      toggleFlashFab: FloatingActionButton,
      context: Context
    ) {
      val c = camera.cameraXSensor.camera
      if (c!!.cameraInfo.torchState.value == TorchState.ON) {
        // Turn off flash
        c.cameraControl.enableTorch(false)
        toggleFlashFab.setImageResource(R.drawable.flashlight_off)
        toggleFlashFab.backgroundTintList =
          ColorStateList.valueOf(ContextCompat.getColor(context, android.R.color.black))
      } else {
        // Turn on flash
        c.cameraControl.enableTorch(true)
        toggleFlashFab.setImageResource(R.drawable.flashlight_on)
        toggleFlashFab.backgroundTintList =
          ColorStateList.valueOf(ContextCompat.getColor(context, R.color.colorPrimary))
      }
    }

    fun sensorsInvolved(captureType: CaptureType): List<InternalSensorType> {
      return when (captureType) {
        CaptureType.VIDEO_PPG -> listOf(InternalSensorType.CAMERA)
        CaptureType.IMAGE -> listOf(InternalSensorType.CAMERA)
      }
    }
  }
}
