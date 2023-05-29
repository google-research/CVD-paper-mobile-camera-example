package com.google.android.sensory.sensing_sdk.db

import com.google.android.sensory.sensing_sdk.model.CaptureInfo
import com.google.android.sensory.sensing_sdk.model.RequestStatus
import com.google.android.sensory.sensing_sdk.model.ResourceInfo
import com.google.android.sensory.sensing_sdk.model.UploadRequest

internal interface Database {
  suspend fun addCaptureInfo(captureInfo: CaptureInfo): String
  suspend fun addResourceInfo(resourceInfo: ResourceInfo): String
  suspend fun addUploadRequest(uploadRequest: UploadRequest): String
  suspend fun listResourceInfoForParticipant(participantId: String): List<ResourceInfo>
  suspend fun listResourceInfoInCapture(captureId: String): List<ResourceInfo>
  suspend fun listUploadRequests(status: RequestStatus): List<UploadRequest>
  suspend fun updateUploadRequest(uploadRequest: UploadRequest)
  suspend fun updateResourceInfo(resourceInfo: ResourceInfo)
  suspend fun getResourceInfo(resourceInfoId: String): ResourceInfo?
}