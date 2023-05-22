package com.google.android.sensory.sensing_sdk

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LiveData
import com.google.android.sensory.sensing_sdk.capture.model.CaptureSettings
import com.google.android.sensory.sensing_sdk.model.CaptureType
import com.google.android.sensory.sensing_sdk.model.UploadRequest
import com.google.android.sensory.sensing_sdk.model.UploadResult
import kotlinx.coroutines.flow.Flow

interface SensingEngine {

  /** Responsible for capturing sensor(s) data(s) for the given captureType and storing the data locally.
   * @param folderId Sort of like participant id. All captures go inside this folder.
   * @param captureType type of capture like PPG, IMAGE, etc type of sensor data to be captured
   * @param captureSettings sensor capture settings
   * @param captureId Id for each captureSenorData API call. This is provided back to the invoker within [FragmentResult].
   * If this is non-null then the folder associated with this captureId is deleted and all resources are re-captured.
   * [TODO] The API could change to accept [CaptureInfo] object that encapsulates captureId, captureType and captureSettings
   */
  fun captureSensorData(
    context: FragmentActivity,
    folderId: String,
    captureType: CaptureType,
    captureSettings: CaptureSettings,
    captureId: String?
  )

  /** Lists all ResourceInfo for a captureId. Applications could use the captureId returned */
  suspend fun listResourceInfo(captureId: String)

  /** To support 3P apps*/
  suspend fun captureSensorData(pendingIntent: Intent)

  /** Uploading local sensor data. API could change to support resumable uploads.*/
  suspend fun syncUpload(upload: (suspend (List<UploadRequest>) -> Flow<UploadResult>))

  /** Delete data stored in blobstore*/
  suspend fun deleteSensorData(uploadURL: String)
  suspend fun deleteSensorMetaData(uploadURL: String)
}