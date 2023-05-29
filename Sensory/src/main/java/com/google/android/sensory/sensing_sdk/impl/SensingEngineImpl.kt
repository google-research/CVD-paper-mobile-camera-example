package com.google.android.sensory.sensing_sdk.impl

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import com.google.android.sensory.sensing_sdk.SensingEngine
import com.google.android.sensory.sensing_sdk.UploadConfiguration
import com.google.android.sensory.sensing_sdk.capture.CaptureFragment
import com.google.android.sensory.sensing_sdk.capture.CaptureManager
import com.google.android.sensory.sensing_sdk.capture.CaptureSettings
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking

/** context should be a FragmentActivity
 * @param context AppCompatActivity context to access fragmentManager to launch fragments, to access files and resources in the application context.*/
@ExperimentalCamera2Interop
internal class SensingEngineImpl(
  private val database: Database,
  private val context: Context,
  private val uploadConfiguration: UploadConfiguration
): SensingEngine {

  private val captureManager = CaptureManager(context)
  override fun captureFragment(
    participantId: String,
    captureType: CaptureType,
    captureSettings: CaptureSettings,
    captureId: String?
  ): CaptureFragment {
    val captureInfo = CaptureInfo(
      participantId = participantId,
      captureType = captureType,
      captureFolder = "Sensory/Participant_${participantId}/${captureSettings.title}",
      /** Set captureId if null*/
      captureId = captureId ?: UUID.randomUUID().toString(),
      captureSettings = captureSettings
    )
    if(captureId != null){
      // delete everything in folder associated with this captureId to re-capture
      val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), captureInfo.captureFolder)
      file.deleteRecursively()
    }
    return captureManager.createCaptureFragment(captureInfo, onCaptureComplete = this::onCaptureComplete)
  }

  /** Responsible for creating resource records for captured data and completing upload setup.
   * Limitation: All captured data and metadata are stored in the same folder and zipped for uploading.
   * 1. add [CaptureInfo] to the database
   * 2. read map for a capture type, for each sensor type:-
   * 3. create resourceinfo for it
   * 4. save in database
   * 5. zip the [captureInfo.captureFolder]/[sensorType] folder
   * 6. create uploadrequest for it
   * 7. save in database
   * ResourceInfo stores relative URI of actual file for the resource captured.
   * UploadRequest stores location to the zipped file to be uploaded.
   * [TODO] Later this API could be exposed if application developers want to use the upload mechanism of this SDK.
   * [TODO] Support uploading of any mime type.
   * [TODO] We need to avoid runBlocking as it can run on the main thread.*/
  private fun onCaptureComplete(captureInfo: CaptureInfo): String {
    runBlocking {
      database.addCaptureInfo(captureInfo)
      CaptureManager.sensorsInvolved(captureInfo.captureType).forEach {
        val resourceFolderRelativePath = getResourceFolderRelativePath(it, captureInfo)
        val uploadRelativeUrl = "/$resourceFolderRelativePath.zip"
        val resourceInfo = ResourceInfo(
          resourceInfoId = UUID.randomUUID().toString(),
          captureId = captureInfo.captureId,
          participantId = captureInfo.participantId,
          captureType = captureInfo.captureType,
          title = captureInfo.captureSettings.title,
          fileType = resourceInfoFileType(it, captureInfo),
          resourceFolderPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath + "/$resourceFolderRelativePath",
          uploadURL = uploadConfiguration.getBlobStorageAccessURL() + uploadRelativeUrl,
          status = RequestStatus.PENDING
        )
        database.addResourceInfo(resourceInfo)
        /** Zipping logic from: https://stackoverflow.com/a/63828765*/
        /** CaptureManager stores files here*/
        // Folder location: Downloads/Sensory/Participant_<participantId>/<captureType>/<sensorType>
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
          uploadURL = uploadRelativeUrl,
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

  override suspend fun listResourceInfoForParticipant(participantId: String): List<ResourceInfo> {
    return database.listResourceInfoForParticipant(participantId)
  }

  override suspend fun listResourceInfoInCapture(captureId: String): List<ResourceInfo> {
    return database.listResourceInfoInCapture(captureId)
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
    fun getResourceFolderRelativePath(sensorType: SensorType, captureInfo: CaptureInfo): String{
      return when(captureInfo.captureType){
        CaptureType.IMAGE -> "${captureInfo.captureFolder}/${sensorType.name}"
        CaptureType.VIDEO_PPG -> "${captureInfo.captureFolder}/${sensorType.name}"
      }
    }
  }
}