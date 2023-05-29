package com.google.android.sensory.sensing_sdk.model

/** Data class equivalent to ResourceInfoEntity for usage outside database.*/
data class ResourceInfo (
  val resourceInfoId: String,
  val captureId: String,
  val participantId: String,
  val captureType: CaptureType,
  val title: String,
  val fileType: String,
  val resourceFolderPath: String,
  val uploadURL: String,
  var status: RequestStatus
  )