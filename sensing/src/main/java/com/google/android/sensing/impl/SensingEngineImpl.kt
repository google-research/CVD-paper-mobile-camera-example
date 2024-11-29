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

package com.google.android.sensing.impl

import android.content.Context
import android.content.Intent
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import com.google.android.sensing.SensingEngine
import com.google.android.sensing.ServerConfiguration
import com.google.android.sensing.capture.CaptureFragment
import com.google.android.sensing.capture.CaptureUtil
import com.google.android.sensing.capture.SensorCaptureResult
import com.google.android.sensing.db.Database
import com.google.android.sensing.db.ResourceNotFoundException
import com.google.android.sensing.model.CaptureInfo
import com.google.android.sensing.model.CaptureType
import com.google.android.sensing.model.RequestStatus
import com.google.android.sensing.model.ResourceInfo
import com.google.android.sensing.model.SensorType
import com.google.android.sensing.model.UploadRequest
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.time.Instant
import java.util.Date
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * @param database Interface to interact with room database.
 * @param context [Context] to access fragmentManager, to launch fragments, to access files and
 * resources in the application context.
 * @param serverConfiguration
 */
@ExperimentalCamera2Interop
internal class SensingEngineImpl(
  private val database: Database,
  private val context: Context,
  private val serverConfiguration: ServerConfiguration?,
) : SensingEngine {

  /** TODO Move zipping and creation of UploadRequest to sync section. */
  override suspend fun onCaptureCompleteCallback(captureInfo: CaptureInfo) = flow {
    try {
      val inRecordCaptureInfo =
        if (captureInfo.recapture == true) {
          getCaptureInfo(captureInfo.captureId!!)
        } else {
          getCaptureInfoByFolder(captureInfo.captureFolder)
        }
      inRecordCaptureInfo?.let { info ->
        deleteDataInCapture(info.captureId!!, captureInfo.recapture == true)
        moveDataFromCacheToFiles(info.captureFolder)
      }
    } catch (_: ResourceNotFoundException) {}
    database.addCaptureInfo(captureInfo)
    emit(SensorCaptureResult.CaptureInfoCreated(captureInfo))
    CaptureUtil.sensorsInvolved(captureInfo.captureType).forEach {
      val resourceFolderRelativePath = getResourceFolderRelativePath(it, captureInfo)
      val uploadRelativeUrl = "/$resourceFolderRelativePath.zip"
      val uploadUrl = (serverConfiguration?.getBucketUrl() ?: "") + uploadRelativeUrl
      val resourceInfo =
        ResourceInfo(
          resourceInfoId = UUID.randomUUID().toString(),
          captureId = captureInfo.captureId!!,
          participantId = captureInfo.participantId,
          captureTitle = captureInfo.captureSettings.captureTitle,
          fileType = resourceInfoFileType(it, captureInfo),
          resourceFolderRelativePath = resourceFolderRelativePath,
          uploadURL = uploadUrl,
          status = RequestStatus.PENDING
        )
      database.addResourceInfo(resourceInfo)
      emit(SensorCaptureResult.ResourceInfoCreated(resourceInfo))

      /** [CaptureFragment] stores files in app's internal storage directory */
      val resourceFolder = File(context.filesDir, resourceFolderRelativePath)
      val outputZipFile = resourceFolder.absolutePath + ".zip"

      serverConfiguration?.let {
        createZipFile(resourceFolder, outputZipFile)
        val uploadRequest =
          UploadRequest(
            requestUuid = UUID.randomUUID(),
            resourceInfoId = resourceInfo.resourceInfoId,
            zipFile = outputZipFile,
            fileSize = File(outputZipFile).length(),
            bucketName = serverConfiguration.bucketName,
            uploadRelativeURL = uploadRelativeUrl,
            isMultiPart = serverConfiguration.networkConfiguration.isMultiPart,
            nextPart = 1,
            uploadId = null,
            status = RequestStatus.PENDING,
            lastUpdatedTime = Date.from(Instant.now()),
            fileOffset = 0L
          )
        database.addUploadRequest(uploadRequest)
        emit(SensorCaptureResult.UploadRequestCreated(uploadRequest.requestUuid.toString()))
      }
    }
    emit(SensorCaptureResult.ResourcesStored(captureInfo.captureId!!))
  }

  private suspend fun moveDataFromCacheToFiles(captureFolder: String) {
    withContext(Dispatchers.IO) {
      val sourceFile = File(context.cacheDir, captureFolder)
      val destFile = File(context.filesDir, captureFolder)
      destFile.mkdirs()
      return@withContext sourceFile.renameTo(destFile)
    }
  }

  override suspend fun captureSensorData(pendingIntent: Intent) {
    TODO("Not yet implemented")
  }

  override suspend fun listResourceInfoForParticipants(
    participants: Set<String>
  ): Map<String, List<ResourceInfo>> {
    return database.listResourceInfoForParticipants(participants)
  }

  override suspend fun listUploadRequestForParticipants(
    participants: Set<String>
  ): Map<String, List<UploadRequest>> {
    return database.listUploadRequestForParticipants(participants)
  }

  override suspend fun listResourceInfoInCapture(captureId: String): List<ResourceInfo> {
    return database.listResourceInfoInCapture(captureId)
  }

  override suspend fun getResourceInfo(resourceInfoId: String): ResourceInfo? {
    return try {
      database.getResourceInfo(resourceInfoId)
    } catch (e: ResourceNotFoundException) {
      null
    }
  }

  override suspend fun updateResourceInfo(resourceInfo: ResourceInfo) {
    database.updateResourceInfo(resourceInfo)
  }

  override suspend fun updateUploadRequest(uploadRequest: UploadRequest) {
    return database.updateUploadRequest(uploadRequest)
  }

  override suspend fun listUploadRequest(status: RequestStatus): List<UploadRequest> {
    return database.listUploadRequests(status)
  }

  override suspend fun getCaptureInfo(captureId: String): CaptureInfo {
    return database.getCaptureInfo(captureId)
  }

  override suspend fun getCaptureInfoByFolder(captureFolder: String): CaptureInfo? {
    return database.getCaptureInfoByFolder(captureFolder)
  }

  override suspend fun deleteDataInCapture(captureId: String, isRecapture: Boolean): Boolean {
    val captureInfo =
      try {
        getCaptureInfo(captureId)
      } catch (e: ResourceNotFoundException) {
        null
      } ?: return true

    // Step 1: Delete db records
    database.deleteRecordsInCapture(captureId)
    if (isRecapture) {
      // Step 2: delete the captureFolder
      val captureFile = File(context.filesDir, captureInfo.captureFolder)
      val parentFile = captureFile.parentFile
      val deleted: Boolean
      withContext(Dispatchers.IO) {
        deleted = captureFile.deleteRecursively()
        // delete Participant's folder if there are no data
        if (parentFile?.list()?.isEmpty() == true) {
          parentFile.delete()
        }
      }
      return deleted
    }
    return false
  }

  override suspend fun deleteSensorData(uploadURL: String) {
    TODO("Not yet implemented")
  }

  override suspend fun deleteSensorMetaData(uploadURL: String) {
    TODO("Not yet implemented")
  }

  override suspend fun resetFailedUploadRequests() {
    val failedUploadRequestList: List<UploadRequest> =
      database.listUploadRequests(status = RequestStatus.FAILED)
    // Extract the resourceInfoIds from pending requests for quick lookup
    val pendingResourceIds =
      database.listUploadRequests(status = RequestStatus.PENDING).map { it.resourceInfoId }.toSet()
    // Extract the resourceInfoIds from uploaded requests for quick lookup
    val uploadedResourceIds =
      database.listUploadRequests(status = RequestStatus.UPLOADED).map { it.resourceInfoId }.toSet()
    // Filter failed requests whose resourceInfoId is not in pending and completed requests
    val failedNotInPendingOrCompletedList =
      failedUploadRequestList.filter {
        it.resourceInfoId !in pendingResourceIds && it.resourceInfoId !in uploadedResourceIds
      }

    failedNotInPendingOrCompletedList.forEach { failedRequest ->
      val uploadRequest =
        with(failedRequest) {
          UploadRequest(
            requestUuid = UUID.randomUUID(),
            resourceInfoId = resourceInfoId,
            zipFile = zipFile,
            fileSize = fileSize,
            bucketName = bucketName,
            uploadRelativeURL = uploadRelativeURL,
            isMultiPart = isMultiPart
          )
        }
      with(File(uploadRequest.zipFile)) {
        if (!this.exists()) {
          val folderPath = this.absolutePath.substring(0, this.absolutePath.length - 4)
          if (!File(folderPath).exists()) throw FileNotFoundException()
          val resourceFolder = File(folderPath)
          createZipFile(resourceFolder, this.absolutePath)
        }
      }
      database.addUploadRequest(uploadRequest)
    }
  }

  /** function to create zip file from given path details [resourceFolder] and [outputZipFile] */
  private fun createZipFile(resourceFolder: File, outputZipFile: String) {
    /** Zipping logic from: https://stackoverflow.com/a/63828765 */
    val zipOutputStream = ZipOutputStream(BufferedOutputStream(FileOutputStream(outputZipFile)))
    zipOutputStream.use { zos ->
      resourceFolder.walkTopDown().forEach { file ->
        val zipFileName =
          file.absolutePath.removePrefix(resourceFolder.absolutePath).removePrefix("/")
        val entry = ZipEntry("$zipFileName${(if (file.isDirectory) "/" else "")}")
        zos.putNextEntry(entry)
        if (file.isFile) {
          file.inputStream().use { fis -> fis.copyTo(zos) }
        }
      }
    }
  }

  companion object {
    /** File format for any sensor is taken from [captureSettings.fileTypeMap]. */
    private fun resourceInfoFileType(sensorType: SensorType, captureInfo: CaptureInfo): String {
      return when (sensorType) {
        SensorType.CAMERA -> captureInfo.captureSettings.fileTypeMap[sensorType]!!
      }
    }

    /** Returns relative folder for a specific sensor type. */
    fun getResourceFolderRelativePath(sensorType: SensorType, captureInfo: CaptureInfo): String {
      return when (captureInfo.captureType) {
        CaptureType.IMAGE -> "${captureInfo.captureFolder}/${sensorType.name}"
        CaptureType.VIDEO_PPG -> "${captureInfo.captureFolder}/${sensorType.name}"
      }
    }
  }
}
