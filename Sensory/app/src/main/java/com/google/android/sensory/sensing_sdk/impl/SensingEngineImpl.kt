package com.google.android.sensory.sensing_sdk.impl

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.google.android.sensory.sensing_sdk.SensingEngine
import com.google.android.sensory.sensing_sdk.capture.CaptureManager
import com.google.android.sensory.sensing_sdk.capture.model.CaptureSettings
import com.google.android.sensory.sensing_sdk.db.Database
import com.google.android.sensory.sensing_sdk.model.CaptureInfo
import com.google.android.sensory.sensing_sdk.model.CaptureType
import com.google.android.sensory.sensing_sdk.model.RequestStatus
import com.google.android.sensory.sensing_sdk.model.UploadRequest
import com.google.android.sensory.sensing_sdk.model.UploadResult
import java.lang.Exception
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** context should be a FragmentActivity*/
internal class SensingEngineImpl(private val database: Database): SensingEngine {
  override fun captureSensorData(
    context: AppCompatActivity,
    captureId: String,
    captureType: CaptureType,
    captureSettings: CaptureSettings,
  ){
    val captureInfo = CaptureInfo(captureId, captureType)
    val captureManager = CaptureManager(context, captureInfo)
    captureManager.capture(captureSettings)
    CoroutineScope(Dispatchers.Unconfined).launch {

    }
    // create uploadRequest for the captured data
  }

  override suspend fun captureSensorData(context: AppCompatActivity, appIntent: Intent) {
    TODO("Not yet implemented")
  }

  override suspend fun listResourceInfo(captureId: String) {
    database.listResourceInfo(captureId)
  }

  /**
   * For UploadResult.Started: ...
   * For UploadResult.Success: ...
   * For UploadResult.Failure: ...
   *
   * Trying to upload only PENDING requests. Failed requests are out of scope for now.
   * */
  override suspend fun syncUpload(upload: suspend (List<UploadRequest>) -> Flow<UploadResult>) {
    upload(database.listUploadRequests(RequestStatus.PENDING)).collect{result ->
      val uploadRequest = result.uploadRequest
      val resourceInfo = database.getResourceInfo(uploadRequest.resourceInfoId)
      if(resourceInfo != null) {
        when(result){
          is UploadResult.Started -> {
            uploadRequest.apply{
              lastUpdatedTime = result.startTime
              bytesUploaded = 0
              status = RequestStatus.PENDING
              uploadId = result.uploadId
            }
          }
          is UploadResult.Success -> {
            val totalBytesUploaded = uploadRequest.bytesUploaded + result.bytesUploaded
            uploadRequest.apply {
              lastUpdatedTime = result.lastUploadTime
              bytesUploaded = totalBytesUploaded
              status =
                if (totalBytesUploaded == resourceInfo.fileSize) RequestStatus.UPLOADED else RequestStatus.PENDING
              uploadId = uploadRequest.uploadId
            }
            resourceInfo.apply {
              status = uploadRequest.status
            }
          }
          is UploadResult.Failure -> {
            uploadRequest.apply {
              lastUpdatedTime = uploadRequest.lastUpdatedTime
              bytesUploaded = uploadRequest.bytesUploaded
              status = RequestStatus.FAILED
              uploadId = uploadRequest.uploadId
            }
            resourceInfo.apply {
              status = uploadRequest.status
            }
          }
        }
      } else throw Exception("ResourceInfo with ${uploadRequest.resourceInfoId} not found")
      database.updateUploadRequest(uploadRequest)
      database.updateResourceInfo(resourceInfo)
    }
  }

  override suspend fun deleteSensorData(uploadURL: String) {
    TODO("Not yet implemented")
  }

  override suspend fun deleteSensorMetaData(uploadURL: String) {
    TODO("Not yet implemented")
  }
}