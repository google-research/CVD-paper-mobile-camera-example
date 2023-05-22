package com.google.android.sensory.sensing_sdk.model

import com.google.android.sensory.sensing_sdk.capture.model.CaptureSettings

/** Data class equivalent to CaptureInfoEntity for usage outside database.*/
data class CaptureInfo (
  val folderId: String,
  val captureType: CaptureType,
  val captureFolder: String,
  val captureId: String,
  /** This is not converted to an entity field for now*/
  val captureSettings: CaptureSettings,
  )
