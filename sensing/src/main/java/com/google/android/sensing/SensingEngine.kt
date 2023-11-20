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

import com.google.android.sensing.capture.SensorCaptureResult
import com.google.android.sensing.model.CaptureInfo
import com.google.android.sensing.model.ResourceMetaInfo
import com.google.android.sensing.model.UploadRequest
import com.google.android.sensing.model.UploadResult
import kotlinx.coroutines.flow.Flow

/**
 * The interface that provides APIs for record-keeping and accessing local sensor data.
 *
 * TODO: CRUD APIs for UploadRequest to use only the upload mechanism this Engine provides.
 */
interface SensingEngine {

  /** Get CaptureInfo record given @param captureId */
  suspend fun getCaptureInfo(captureId: String): CaptureInfo

  /**
   * Lists all [ResourceMetaInfo] given a participantId. This will return all ResourceMetaInfo
   * across multiple captures.
   */
  suspend fun listResourceMetaInfoForParticipant(participantId: String): List<ResourceMetaInfo>

  /**
   * Lists all ResourceMetaInfo given a captureId. This will return all ResourceMetaInfo for a
   * single capture.
   */
  suspend fun listResourceMetaInfoInCapture(captureId: String): List<ResourceMetaInfo>

  /**
   * Get [UploadRequest] corresponding to the [ResourceMetaInfo] given
   * [ResourceMetaInfo.resourceMetaInfoId]. Application developers can use this API to monitor not
   * just upload status but also progress.
   */
  suspend fun getUploadRequest(resourceMetaInfoId: String): UploadRequest?

  /** Delete all data associated with [captureId] */
  suspend fun deleteDataInCapture(captureId: String): Boolean

  /**
   * Responsible for record-keeping of the information about a capture. It creates database records
   * ( [CaptureInfoEntity], [ResourceMetaInfoEntity], [UploadRequestEntity]). All captured data and
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

  /**
   * Responsible for record-keeping of the upload status of captured data. Synchronizes the upload
   * results in the database.
   *
   * The [upload] function may initiate multiple server calls. Each call's result can then be used
   * to emit [UploadResult]. The caller should collect these results using [Flow.collect].
   * Additionally, it deletes redundant zipped files after successful upload.
   */
  suspend fun syncUpload(upload: (suspend (List<UploadRequest>) -> Flow<UploadResult>))
}
