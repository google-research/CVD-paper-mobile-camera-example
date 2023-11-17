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
import com.google.android.sensing.model.UploadResult
import kotlinx.coroutines.flow.Flow

/**
 * The interface that provide APIs for bookkeeping and accessing local sensor data.
 *
 * TODO: CRUD APIs for UploadRequest to use only the upload mechanism this Engine provides.
 */
interface SensingEngine {

  /**
   * This callback is used by CaptureFragment to book-keep information about the capture. It creates
   * database records ([CaptureInfoEntity], [ResourceInfoEntity], [UploadRequestEntity]). All
   * captured data and metadata are zipped into one folder for uploading.
   *
   * The application can also independently use this callback to utilize this library's book-keeping
   * mechanism for its own captured data.
   *
   * TODO: Support uploading of any mime type.
   */
  suspend fun onCaptureCompleteCallback(captureInfo: CaptureInfo): Flow<SensorCaptureResult>

  /**
   * Lists all ResourceInfo given a participantId. This will return all ResourceInfo across multiple
   * captures.
   */
  suspend fun listResourceInfoForParticipant(participantId: String): List<ResourceInfo>

  /**
   * Lists all ResourceInfo given a captureId. This will return all ResourceInfo for a single
   * capture.
   */
  suspend fun listResourceInfoInCapture(captureId: String): List<ResourceInfo>

  /** To support 3P apps */
  suspend fun captureSensorData(pendingIntent: Intent)

  /**
   * Synchronizes the upload results in the database.
   *
   * The [upload] function may initiate multiple server calls. Each call's result can then be used
   * to emit [UploadResult]. The caller should collect these results using [Flow.collect].
   * Additionally, it should also delete redundant zipped files after successful upload.
   */
  suspend fun syncUpload(upload: (suspend (List<UploadRequest>) -> Flow<UploadResult>))

  /**
   * Get [UploadRequest] corresponding to the [ResourceInfo] given [ResourceInfo.resourceInfoId].
   * Application developers can use this API to monitor not just upload status but also progress.
   */
  suspend fun getUploadRequest(resourceInfoId: String): UploadRequest?

  /** Delete data stored in blobstore */
  suspend fun deleteSensorData(uploadURL: String)
  suspend fun deleteSensorMetaData(uploadURL: String)

  suspend fun getCaptureInfo(captureId: String): CaptureInfo

  /** Delete data associated with [captureId] */
  suspend fun deleteDataInCapture(captureId: String): Boolean
}
