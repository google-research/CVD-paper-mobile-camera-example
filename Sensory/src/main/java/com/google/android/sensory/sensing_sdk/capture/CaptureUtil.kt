package com.google.android.sensory.sensing_sdk.capture

import android.app.AlertDialog
import android.content.Context
import androidx.camera.core.TorchState
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.sensory.R
import com.google.fitbit.research.sensing.common.libraries.camera.Camera2InteropSensor

class CaptureUtil {
  companion object{
    fun toggleFlash(camera: Camera2InteropSensor, toggleFlash: FloatingActionButton
    ){
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