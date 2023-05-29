package com.google.android.sensory.sensing_sdk.model

import java.util.Date

sealed class UploadResult {
  abstract val uploadRequest: UploadRequest

  data class Started(
    override val uploadRequest: UploadRequest,
    val startTime: Date,
    val uploadId: String,
  ) : UploadResult()

  data class Success(
    override val uploadRequest: UploadRequest,
    val bytesUploaded: Long,
    val lastUploadTime: Date,
  ) : UploadResult()

  data class Completed(override val uploadRequest: UploadRequest, val completeTime: Date) :
    UploadResult()

  data class Failure(override val uploadRequest: UploadRequest, val uploadError: Exception) :
    UploadResult()
}