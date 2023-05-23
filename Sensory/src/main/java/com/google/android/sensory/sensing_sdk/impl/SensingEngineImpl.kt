package com.google.android.sensory.sensing_sdk.impl

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Environment
import androidx.fragment.app.FragmentActivity
import com.google.android.sensory.sensing_sdk.SensingEngine
import com.google.android.sensory.sensing_sdk.UploadConfiguration
import com.google.android.sensory.sensing_sdk.capture.CaptureManager
import com.google.android.sensory.sensing_sdk.capture.model.CaptureSettings
import com.google.android.sensory.sensing_sdk.db.Database
import com.google.android.sensory.sensing_sdk.model.CaptureInfo
import com.google.android.sensory.sensing_sdk.model.CaptureType
import com.google.android.sensory.sensing_sdk.model.RequestStatus
import com.google.android.sensory.sensing_sdk.model.ResourceInfo
import com.google.android.sensory.sensing_sdk.model.SensorType
import com.google.android.sensory.sensing_sdk.model.UploadRequest
import com.google.android.sensory.sensing_sdk.model.UploadResult
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.Date
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** context should be a FragmentActivity
 * @param context AppCompatActivity context to access fragmentManager to launch fragments, to access files and resources in the application context.*/
internal class SensingEngineImpl(
  private val database: Database,
  private val context: Context,
  private val uploadConfiguration: UploadConfiguration
): SensingEngine {

  private val contextWrapper = ContextWrapper(context)
  override fun captureSensorData(
    context: FragmentActivity,
    folderId: String,
    captureType: CaptureType,
    captureSettings: CaptureSettings,
    captureId: String?
  ){
    val captureManager = CaptureManager(context)
    val captureInfo = CaptureInfo(
      folderId = folderId,
      captureType = captureType,
      captureFolder = "Sensory/Participant_${folderId}/${captureType.name}",
      captureId = captureId ?: UUID.randomUUID().toString(),
      captureSettings = captureSettings
    )
    if(captureId != null){
      // delete everything in folder associated with this captureId to re-capture
      val file = contextWrapper.getDir(captureInfo.captureFolder, Context.MODE_PRIVATE)
      file.deleteRecursively()
    }
    runBlocking {
      database.addCaptureInfo(captureInfo)
    }
    captureManager.capture(captureInfo, onCaptureComplete = this::onCaptureComplete)
  }

  /**
   * 1. read map for a capture type, for each sensor type:-
   * 2. create resourceinfo for it
   * 3. save in database
   * 4. zip the [captureInfo.captureFolder]/[sensorType] folder
   * 5. create uploadrequest for it
   * 6. save in database
   * From ContextWrapper.getDir: The returned path may change over time if the calling app is moved to an adopted storage device, so only relative paths should be persisted.
   * ResourceInfo stores local URI to actual file for the resource captured. UploadRequest stores location to the zipped file to be uploaded*/
  @OptIn(DelicateCoroutinesApi::class)
  private fun onCaptureComplete(captureInfo: CaptureInfo): String {
    GlobalScope.launch {
      CaptureManager.sensorsInvolved(captureInfo.captureType).forEach {
        // /data/data/<app>/app_data/Participants/<folderId>/<captureType>/<sensorType>
        val resourceFolderRelativePath = resourceInfoFileUri(it, captureInfo)
        val resourceInfo = ResourceInfo(
          resourceInfoId = UUID.randomUUID().toString(),
          captureId = captureInfo.folderId,
          captureType = captureInfo.captureType,
          fileType = resourceInfoFileType(it, captureInfo),
          fileURI = resourceFolderRelativePath,
          uploadURL = uploadConfiguration.getBucketURL() + "/" + resourceFolderRelativePath,
          status = RequestStatus.PENDING
        )
        database.addResourceInfo(resourceInfo)
        /** Zipping logic from: https://stackoverflow.com/a/63828765*/
        /** CaptureManager stores files here*/
        val resourceFolder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), resourceFolderRelativePath)
        val outputZipFile = resourceFolder.absolutePath + ".zip"
        val zipOutputStream = ZipOutputStream(BufferedOutputStream(FileOutputStream(outputZipFile)))
        zipOutputStream.use { zos ->
          resourceFolder.walkTopDown().forEach { file ->
            val zipFileName = file.absolutePath.removePrefix(resourceFolder.absolutePath).removePrefix("/")
            val entry = ZipEntry( "$zipFileName${(if (file.isDirectory) "/" else "" )}")
            zos.putNextEntry(entry)
            if (file.isFile) {
              file.inputStream().use { fis -> fis.copyTo(zos) }
            }
          }
        }
        val uploadRequest = UploadRequest(
          requestUuid = UUID.randomUUID(),
          resourceInfoId = resourceInfo.resourceInfoId,
          zipFile = outputZipFile,
          fileSize = File(outputZipFile).length(),
          uploadURL = "$resourceFolderRelativePath.zip",
          lastUpdatedTime = Date.from(Instant.now()),
          bytesUploaded = 0L,
          status = RequestStatus.PENDING,
          nextPart = 1,
          uploadId = null
        )
        database.addUploadRequest(uploadRequest)
      }
    }
    return captureInfo.captureId
  }

  override suspend fun captureSensorData(pendingIntent: Intent) {
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
    // uploadRequest.nextPart += 1
    // uploadRequest.bytesUploaded += chunkSize
    // uploadRequest.status = RequestStatus.UPLOADING
    // println("Uploaded part ${uploadRequest.nextPart - 1} until ${uploadRequest.bytesUploaded}")
    // return uploadRequest.status
    upload(database.listUploadRequests(RequestStatus.PENDING)).collect{ result ->
      val uploadRequest = result.uploadRequest
      val requestsPreviousStatus = uploadRequest.status
      when(result){
        is UploadResult.Started -> {
          uploadRequest.apply{
            lastUpdatedTime = result.startTime
            bytesUploaded = 0
            status = RequestStatus.UPLOADING
            uploadId = result.uploadId
          }
        }
        is UploadResult.Success -> {
          uploadRequest.apply {
            lastUpdatedTime = result.lastUploadTime
            bytesUploaded = uploadRequest.bytesUploaded + result.bytesUploaded
          }
        }
        is UploadResult.Completed -> {
          assert(uploadRequest.bytesUploaded == uploadRequest.fileSize)
          uploadRequest.apply {
            lastUpdatedTime = result.completeTime
            status = RequestStatus.UPLOADED
          }
        }
        is UploadResult.Failure -> {
          uploadRequest.apply {
            lastUpdatedTime = uploadRequest.lastUpdatedTime
            status = RequestStatus.FAILED
          }
        }
      }
      database.updateUploadRequest(uploadRequest)
      // Update status of ResourceInfo only when UploadRequest.status changes
      if(requestsPreviousStatus != uploadRequest.status){
        val resourceInfo = database.getResourceInfo(uploadRequest.resourceInfoId)!!
        resourceInfo.apply {
          status = uploadRequest.status
        }
        database.updateResourceInfo(resourceInfo)
      }
    }
  }

  override suspend fun deleteSensorData(uploadURL: String) {
    TODO("Not yet implemented")
  }

  override suspend fun deleteSensorMetaData(uploadURL: String) {
    TODO("Not yet implemented")
  }

  companion object{
    /** File format is configured in captureSettings. */
    private fun resourceInfoFileType(sensorType: SensorType, captureInfo: CaptureInfo): String{
      return when(sensorType){
        SensorType.CAMERA -> captureInfo.captureSettings.fileTypeMap[sensorType]!!
      }
    }

    /** Returns folder for a specific sensor type in For both captureType we zip the stored files into a folder for uploading*/
    fun resourceInfoFileUri(sensorType: SensorType, captureInfo: CaptureInfo): String{
      return when(captureInfo.captureType){
        CaptureType.IMAGE -> "${captureInfo.captureFolder}/${sensorType.name}"
        CaptureType.VIDEO_PPG -> "${captureInfo.captureFolder}/${sensorType.name}"
      }
    }
  }
}