package com.google.android.sensory.sensing_sdk.model

import java.util.Date

data class UploadRequest(
  val requestId: String,
  val resourceInfoId: String,
  val uploadURL: String,
  var lastUpdatedTime: Date,
  var bytesUploaded: Long,
  var status: RequestStatus,
  var nextPart: Int,
  /** Null because this is updated from first upload response.*/
  var uploadId: String? = null
)