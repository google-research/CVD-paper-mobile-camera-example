package com.google.android.sensory.sensing_sdk.model

import java.util.Date
import java.util.UUID

/** Data class equivalent to UploadRequestEntity for usage outside database.*/
data class UploadRequest(
  val requestUuid: UUID,
  val resourceInfoId: String,
  val zipFile: String,
  val fileSize: Long,
  val uploadURL: String,
  var lastUpdatedTime: Date,
  var bytesUploaded: Long,
  var status: RequestStatus,
  var nextPart: Int,
  var uploadId: String? = null
)