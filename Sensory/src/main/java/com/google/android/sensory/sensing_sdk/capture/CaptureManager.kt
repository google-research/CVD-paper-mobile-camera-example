package com.google.android.sensory.sensing_sdk.capture

import android.content.Context
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import com.google.android.sensory.sensing_sdk.model.CaptureInfo
import com.google.android.sensory.sensing_sdk.model.CaptureType
import com.google.android.sensory.sensing_sdk.model.SensorType

/** Responsible for instantiating and returning CaptureFragment. */
@ExperimentalCamera2Interop
class CaptureManager(private val context: Context) {
  fun createCaptureFragment(
    captureInfo: CaptureInfo,
    onCaptureComplete: ((CaptureInfo) -> String),
  ): CaptureFragment {
    return CaptureFragment(
      captureInfo,
      onCaptureComplete
    )
  }

  companion object {
    fun sensorsInvolved(captureType: CaptureType): List<SensorType> {
      return when (captureType) {
        CaptureType.VIDEO_PPG -> listOf(SensorType.CAMERA)
        CaptureType.IMAGE -> listOf(SensorType.CAMERA)
      }
    }
  }
}