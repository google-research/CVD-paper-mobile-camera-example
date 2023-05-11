package com.google.android.sensory.sensing_sdk

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import com.google.android.sensory.sensing_sdk.capture.model.CaptureSettings
import com.google.android.sensory.sensing_sdk.model.CaptureType
import com.google.android.sensory.sensing_sdk.model.UploadRequest
import com.google.android.sensory.sensing_sdk.model.UploadResult
import kotlinx.coroutines.flow.Flow

interface SensingEngine {

  /** Responsible for capturing sensor(s) data(s) for the given captureType and storing the data locally.
   * @param context AppCompatActivity context to access fragmentManager to launch fragments, to access files and resources in the application context.
   * @param captureType type of capture like PPG, IMAGE, etc type of sensor data to be captured
   * @param captureSettings sensor capture settings
   * @param captureListener listens to capture events
   * @param instructionFragment instruction page for a captureType. This is needed because for same captureType instructions could be different.
   * [TODO] The API could change to accept [CaptureInfo] object that encapsulates captureId, captureType and captureSettings
   */
  fun captureSensorData(
    context: AppCompatActivity,
    captureId: String,
    captureType: CaptureType,
    captureSettings: CaptureSettings,
  )

  /** Lists all ResourceInfo for a captureId. Applications could use the captureId returned */
  suspend fun listResourceInfo(captureId: String)

  /** To support 3P apps*/
  suspend fun captureSensorData(
    context: AppCompatActivity,
    appIntent: Intent
  )

  /** Uploading local sensor data. API could change to support resumable uploads.*/
  suspend fun syncUpload(upload: (suspend (List<UploadRequest>) -> Flow<UploadResult>))

  /** Delete data stored in blobstore*/
  suspend fun deleteSensorData(uploadURL: String)
  suspend fun deleteSensorMetaData(uploadURL: String)
}