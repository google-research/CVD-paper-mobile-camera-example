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

package com.google.android.sensory.sensing_sdk

import android.content.Intent
import com.google.android.sensory.sensing_sdk.capture.CaptureFragment
import com.google.android.sensory.sensing_sdk.capture.SensorCaptureResult
import com.google.android.sensory.sensing_sdk.model.CaptureInfo
import com.google.android.sensory.sensing_sdk.model.RequestStatus
import com.google.android.sensory.sensing_sdk.model.ResourceInfo
import com.google.android.sensory.sensing_sdk.model.UploadRequest
import com.google.android.sensory.sensing_sdk.model.UploadResult
import kotlinx.coroutines.flow.Flow

/**
 * The Sensing Engine interface that handles the local storage of captured resources. It also acts
 * as a factory to create [CaptureFragment]. [TODO] For application developers to use the upload
 * mechanism ONLY [onCaptureCompleteCallback] is not enough. We need to add CRUD APIs for
 * UploadRequest also.
 */
interface SensingEngine {

  /**
   * Returns [CaptureFragment] for given captureType. This API is needed because:-
   * 1. Each invocation requires different capture settings and hence can't be globally configured.
   * 2. Since SensingEngine is also responsible for updating database records, this API acts as a
   * factory and creates a [CaptureFragment] instance in a way that [onCaptureCompleteCallback] is
   * called once capture is completed.
   * 3. We want application developers to have more control over the UI using the returned fragment.
   * Application developers are responsible for handling lifecycle of the fragment returned which
   * should be straight forward since we can fairly assume that capturing via camera is a foreground
   * 1-time activity.
   * 4. This way we also leave other Fragments involved in capturing, like InstructionFragments, out
   * of scope of this Sensing SDK.
   * @param participantId Used to name the capture folder (like Participant_<participantId>).
   * @param captureType type of capture like VIDEO_PPG, IMAGE, etc type of sensor data to be
   * captured
   * @param captureSettings sensor capture settings
   * @param captureId Id for each captureSenorData API call. This is provided back to the invoker
   * within [FragmentResult]. If this is non-null then the folder associated with this captureId is
   * deleted and all resources are re-captured. [TODO] The API could change to accept [CaptureInfo]
   * object that encapsulates captureId, captureType and captureSettings
   */
  fun captureFragment(
    captureInfo: CaptureInfo,
    sensorCaptureResultCollector: suspend ((Flow<SensorCaptureResult>) -> Unit)
  ): CaptureFragment

  /**
   * Responsible for creating resource records for captured data and completing upload setup.
   * Limitation: All captured data and metadata are stored in the same folder and zipped for
   * uploading.
   * 1. Save [CaptureInfo] in the database
   * 2. read map for a capture type, for each sensor type:-
   * ```
   *      a. create [ResourceInfo] for it and save it in the database
   *      b. emit [SensorCaptureResult.StateChange]
   *      c. zip the [captureInfo.captureFolder]/[sensorType] folder
   *      d. create [UploadRequest] for it and save it in the database [TODO]
   * ```
   * Support uploading of any mime type.
   */
  suspend fun onCaptureCompleteCallback(captureInfo: CaptureInfo): Flow<SensorCaptureResult>

  /**
   * Lists all ResourceInfo given a participantId. This will return all ResourceInfo across multiple
   * capturings.
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
   * [SensorDataUploadWorker] invokes this API to fetch and update [RequestStatus.PENDING] records
   * to upload.
   */
  suspend fun syncUpload(upload: (suspend (List<UploadRequest>) -> Flow<UploadResult>))

  /** Delete data stored in blobstore */
  suspend fun deleteSensorData(uploadURL: String)
  suspend fun deleteSensorMetaData(uploadURL: String)
}
