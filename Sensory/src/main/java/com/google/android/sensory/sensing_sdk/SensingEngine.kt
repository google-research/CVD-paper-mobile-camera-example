/*
 * Copyright 2022 Google LLC
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
import com.google.android.sensory.sensing_sdk.capture.CaptureSettings
import com.google.android.sensory.sensing_sdk.model.CaptureType
import com.google.android.sensory.sensing_sdk.model.ResourceInfo
import com.google.android.sensory.sensing_sdk.model.UploadRequest
import com.google.android.sensory.sensing_sdk.model.UploadResult
import kotlinx.coroutines.flow.Flow

interface SensingEngine {

  /**
   * Returns [CaptureFragment] for given captureType. This API is needed because:-
   * 1. Unlike some global config settings (like in SDC's QuestionnaireFragment ), each capture may
   * require different capture settings.
   * 2. Given each API call may have different capture settings business logic should be managed by
   * a layer above [CaptureFragment] (not even CaptureViewModel).
   * ```
   *    Hence we want business logic to reside outside the CaptureFragment, in our case its [SensingEngine]
   *    Alternatively, one could use CaptureFragment directly with it accessing the SensingEngine but this looks bad design principle.
   *    Given Fragment represents a reusable portion of your app's UI, the application developers getting this fragment becomes responsible for handling it. But assuming the capturing is a foreground activity, handling it should not be a difficult task.
   * ```
   * 3. We want application developers to have more control over the UI using the returned fragment.
   * This way we also leave other Fragments involved in capturing, like InstructionFragment, out of
   * scope.
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
    participantId: String,
    captureType: CaptureType,
    captureSettings: CaptureSettings,
    captureId: String? = null,
  ): CaptureFragment

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

  /** Uploading local sensor data. API could change to support resumable uploads. */
  suspend fun syncUpload(upload: (suspend (List<UploadRequest>) -> Flow<UploadResult>))

  /** Delete data stored in blobstore */
  suspend fun deleteSensorData(uploadURL: String)
  suspend fun deleteSensorMetaData(uploadURL: String)
}
