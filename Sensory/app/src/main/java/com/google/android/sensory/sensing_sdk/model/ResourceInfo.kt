package com.google.android.sensory.sensing_sdk.model

data class ResourceInfo (
  val resourceInfoId: String,
  val captureId: String,
  val fileSize: Long,
  val fileType: String,
  val fileURI: String,
  /** uploadUrl should be known at instance creation and not changed post partial uploading.*/
  val uploadURL: String,
  var status: RequestStatus
  )