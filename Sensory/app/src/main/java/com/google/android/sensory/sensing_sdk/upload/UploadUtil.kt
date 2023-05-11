package com.google.android.sensory.sensing_sdk.upload

import com.google.android.sensory.sensing_sdk.model.RequestStatus
import com.google.android.sensory.sensing_sdk.model.ResourceInfo
import com.google.android.sensory.sensing_sdk.model.UploadRequest
import com.google.android.sensory.sensing_sdk.model.UploadResult
import java.time.Instant
import java.util.Date
import java.util.UUID

class UploadUtil {
  companion object{
    fun deriveUploadRequest(resourceInfo: ResourceInfo) = UploadRequest(
        requestId = UUID.randomUUID().toString(),
        resourceInfoId = resourceInfo.resourceInfoId,
        uploadURL = resourceInfo.uploadURL,
        lastUpdatedTime = Date.from(Instant.now()),
        bytesUploaded = 0L,
        status = RequestStatus.PENDING,
        nextPart = 1,
        uploadId = null
      )
  }
}