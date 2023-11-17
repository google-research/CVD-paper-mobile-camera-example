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
import com.google.android.sensing.model.ResourceMetaInfo
import com.google.android.sensing.model.UploadRequest
import com.google.android.sensing.model.UploadStatus

/** The interface for the sensor resources database. */
internal interface Database {
  suspend fun addCaptureInfo(captureInfo: CaptureInfo): String
  suspend fun addResourceMetaInfo(resourceMetaInfo: ResourceMetaInfo): String
  suspend fun addUploadRequest(uploadRequest: UploadRequest): String
  suspend fun listResourceMetaInfoForParticipant(participantId: String): List<ResourceMetaInfo>
  suspend fun listResourceMetaInfoInCapture(captureId: String): List<ResourceMetaInfo>
  suspend fun listUploadRequests(status: UploadStatus): List<UploadRequest>
  suspend fun updateUploadRequest(uploadRequest: UploadRequest)
  suspend fun updateResourceMetaInfo(resourceMetaInfo: ResourceMetaInfo)
  suspend fun getResourceMetaInfo(resourceMetaInfoId: String): ResourceMetaInfo
  suspend fun getCaptureInfo(captureId: String): CaptureInfo
  suspend fun deleteUploadRequest(resourceMetaInfoId: String): Boolean
  suspend fun deleteResourceMetaInfo(resourceMetaInfoId: String): Boolean
  suspend fun deleteCaptureInfo(captureId: String): Boolean
  suspend fun deleteRecordsInCapture(captureId: String): Boolean
}
