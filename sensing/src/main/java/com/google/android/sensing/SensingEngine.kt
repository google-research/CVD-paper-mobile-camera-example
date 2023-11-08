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
import com.google.android.sensing.model.RequestStatus
import com.google.android.sensing.model.ResourceInfo
import com.google.android.sensing.model.UploadRequest
import com.google.android.sensing.model.UploadResult
import kotlinx.coroutines.flow.Flow

/**
 * The Interface that defines APIs for tracking and handling sensing data. As a tracker it has
 * following responsibilities:-
 * 1. Track capture of data and create database records
 * 2. Track upload of data and update database records
 *
 * As a handler it has following responsibilities:-
 * 1. CRUD APIs to access capture information
 *
 * TODO: CRUD APIs for UploadRequest to use only the upload mechanism this Engine provides.
 */
interface SensingEngine {

  /**
   * Responsible for creating database records ([CaptureInfoEntity], [ResouurceInfoEntity],
   * [UploadRequestEntity]) to track captured data. This API is triggered by [CaptureViewModel]
   * after completion of capturing. Limitation: All captured data and metadata are stored in the
   * same folder and zipped for uploading.
   * 1. Delete all data for [captureInfo.captureId] if present.
   * 2. Move data from cache to files directory in internal storage.
   * 2. Save [CaptureInfo] in the database.
   * 3. For each sensor involved in the capture:-
   * ```
   *      a. create [ResourceInfo] record.
   *      d. create [UploadRequest] record.
   * ```
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
   * [SensorDataUploadWorker] invokes this API to fetch [RequestStatus.PENDING] records to upload
   * and then update those records on collecting [UploadResult]s. Additionally, it also deletes
   * captured files and folders after successful upload.
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
