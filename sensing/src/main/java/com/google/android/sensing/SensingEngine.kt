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

package com.google.android.sensing

import android.content.Intent
import com.google.android.sensing.capture.SensorCaptureResult
import com.google.android.sensing.model.CaptureInfo
import com.google.android.sensing.model.ResourceInfo
import com.google.android.sensing.model.UploadRequest
import com.google.android.sensing.model.UploadRequestStatus
import kotlinx.coroutines.flow.Flow

/**
 * The interface that provides APIs for record-keeping and accessing local sensor data.
 *
 * TODO: CRUD APIs for UploadRequest to use only the upload mechanism this Engine provides.
 */
interface SensingEngine {

  /** Get CaptureInfo record given @param captureId */
  suspend fun getCaptureInfo(captureId: String): CaptureInfo

  /** Get [ResourceInfo] given [resourceInfoId]. */
  suspend fun getResourceInfo(resourceInfoId: String): ResourceInfo?

  /** Update given [resourceInfo]. */
  suspend fun updateResourceInfo(resourceInfo: ResourceInfo)

  /** Update given [uploadRequest] in database. */
  suspend fun updateUploadRequest(uploadRequest: UploadRequest)

  /**
   * List [UploadRequest] of a particular [uploadRequestStatus]. This can be used to fetch all
   * requests that needs to be uploaded.
   */
  suspend fun listUploadRequest(uploadRequestStatus: UploadRequestStatus): List<UploadRequest>

  /**
   * Lists all ResourceInfo given a participantId. This will return all ResourceInfo across multiple
   * capturing.
   */
  suspend fun listResourceInfoForParticipant(participantId: String): List<ResourceInfo>

  /**
   * Lists all ResourceInfo given a captureId. This will return all ResourceInfo for a single
   * capture.
   */
  suspend fun listResourceInfoInCapture(captureId: String): List<ResourceInfo>

  /** Delete all data associated with [captureId] */
  suspend fun deleteDataInCapture(captureId: String): Boolean

  /**
   * Responsible for record-keeping of the information about a capture. It creates database records
   * ( [CaptureInfoEntity], [ResourceInfoEntity], [UploadRequestEntity]). All captured data and
   * metadata are zipped into one folder for uploading.
   *
   * [CaptureFragment] invokes this API on successful capture.
   *
   * The application can use this API to utilize this library's record-keeping mechanism for its own
   * captured data.
   *
   * TODO: Support uploading of any mime type.
   */
  suspend fun onCaptureComplete(captureInfo: CaptureInfo): Flow<SensorCaptureResult>

  /** To support 3P apps */
  suspend fun captureSensorData(pendingIntent: Intent)
}
