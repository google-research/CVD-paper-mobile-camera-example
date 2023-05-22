package com.google.android.sensory.sensing_sdk.capture.model

import androidx.camera.camera2.interop.CaptureRequestOptions
import com.google.android.sensory.sensing_sdk.model.SensorType

data class CaptureSettings (
  /** The file format captured sensor data should be stored in. TODO: Need to have some defaults.*/
  val fileTypeMap: Map<SensorType, String>,
)