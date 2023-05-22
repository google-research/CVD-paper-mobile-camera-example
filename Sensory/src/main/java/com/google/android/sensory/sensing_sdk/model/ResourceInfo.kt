package com.google.android.sensory.sensing_sdk.model

import java.util.UUID

/** Data class equivalent to ResourceInfoEntity for usage outside database.*/
data class ResourceInfo (
  val resourceInfoId: String,
  val captureId: String,
  val captureType: CaptureType,
  val fileType: String,
  val fileURI: String,
  val uploadURL: String,
  var status: RequestStatus
  )