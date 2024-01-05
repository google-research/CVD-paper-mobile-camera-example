/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.sensing.db

import com.google.android.sensing.model.CaptureInfo
import com.google.android.sensing.model.ResourceInfo
import com.google.android.sensing.model.UploadRequest
import com.google.android.sensing.model.UploadRequestStatus

/** The interface for the sensor resources database. */
internal interface Database {
  suspend fun addCaptureInfo(captureInfo: CaptureInfo): String
  suspend fun addResourceInfo(resourceInfo: ResourceInfo): String
  suspend fun addUploadRequest(uploadRequest: UploadRequest): String
  suspend fun listResourceInfoForParticipant(participantId: String): List<ResourceInfo>
  suspend fun listResourceInfoInCapture(captureId: String): List<ResourceInfo>
  suspend fun listUploadRequests(status: UploadRequestStatus): List<UploadRequest>
  suspend fun updateUploadRequest(uploadRequest: UploadRequest)
  suspend fun updateResourceInfo(resourceInfo: ResourceInfo)
  suspend fun getResourceInfo(resourceInfoId: String): ResourceInfo
  suspend fun getCaptureInfo(captureId: String): CaptureInfo
  suspend fun deleteRecordsInCapture(captureId: String): Boolean
}
