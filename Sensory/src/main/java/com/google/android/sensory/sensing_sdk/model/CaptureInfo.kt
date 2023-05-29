package com.google.android.sensory.sensing_sdk.model

import com.google.android.sensory.sensing_sdk.capture.CaptureSettings

/** Data class equivalent to CaptureInfoEntity for usage outside database.*/
data class CaptureInfo(
  val participantId: String,
  val captureType: CaptureType,
  val captureFolder: String,
  val captureId: String,
  /** This is not persisted in database for now*/
  val captureSettings: CaptureSettings,
)
