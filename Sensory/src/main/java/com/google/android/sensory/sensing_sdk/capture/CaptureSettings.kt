package com.google.android.sensory.sensing_sdk.capture

import com.google.android.sensory.sensing_sdk.model.SensorType

data class CaptureSettings(
  /** The file format captured sensor data should be stored in. TODO: Need to have some defaults.*/
  val fileTypeMap: Map<SensorType, String>,
  val metaDataTypeMap: Map<SensorType, String>,
  val title: String,
)