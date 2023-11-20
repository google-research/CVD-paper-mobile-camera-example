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
import com.google.android.sensing.model.ResourceMetaInfo
import com.google.android.sensing.model.SensorType
import com.google.android.sensing.model.UploadRequest
import com.google.android.sensing.model.UploadResult
import com.google.android.sensing.model.UploadStatus
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.Date
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
  private val serverConfiguration: ServerConfiguration,
) : SensingEngine {

  override suspend fun onCaptureComplete(captureInfo: CaptureInfo) = flow {
    assert(captureInfo.captureId != null)
    with(captureInfo) {
      if (recapture == true) {
        try {
          val inRecordCaptureInfo = getCaptureInfo(captureId!!)
          deleteDataInCapture(inRecordCaptureInfo.captureId!!)
          moveDataFromCacheToFiles(inRecordCaptureInfo.captureFolder)
        } catch (_: ResourceNotFoundException) {}
      } // else we don't need to worry about it because the capture module stored data in the
      // internal
      // filesDir
      database.addCaptureInfo(captureInfo)
      emit(SensorCaptureResult.CaptureInfoCreated(captureId!!))
      CaptureUtil.sensorsInvolved(captureType).forEach {
        val resourceMetaInfo = createResourceMetaInfo(it, captureInfo)
        database.addResourceMetaInfo(resourceMetaInfo)
        emit(SensorCaptureResult.ResourceMetaInfoCreated(resourceMetaInfo.resourceMetaInfoId))

        val uploadRequest = createUploadRequest(it, resourceMetaInfo)
        database.addUploadRequest(uploadRequest)
        emit(SensorCaptureResult.UploadRequestCreated(uploadRequest.requestUuid.toString()))
      }
      emit(SensorCaptureResult.ResourcesStored(captureId!!))
    }
  }

  private suspend fun moveDataFromCacheToFiles(captureFolder: String) {
    withContext(Dispatchers.IO) {
      val sourceFile = File(context.cacheDir, captureFolder)
      val destFile = File(context.filesDir, captureFolder)
      destFile.mkdirs()
      return@withContext sourceFile.renameTo(destFile)
    }
  }

  private fun createResourceMetaInfo(
    sensorType: SensorType,
    captureInfo: CaptureInfo
  ): ResourceMetaInfo {
    val resourceFolderRelativePath = getResourceFolderRelativePath(sensorType, captureInfo)
    val uploadRelativeUrl = "/$resourceFolderRelativePath.zip"
    return ResourceMetaInfo(
      resourceMetaInfoId = UUID.randomUUID().toString(),
      captureId = captureInfo.captureId!!,
      participantId = captureInfo.participantId,
      captureTitle = captureInfo.captureSettings!!.captureTitle,
      fileType = resourceMetaInfoFileType(sensorType, captureInfo),
      resourceFolderRelativePath = resourceFolderRelativePath,
      uploadURL = serverConfiguration.getBucketUrl() + uploadRelativeUrl,
      uploadStatus = UploadStatus.PENDING
    )
  }

  private suspend fun createUploadRequest(
    sensorType: SensorType,
    resourceMetaInfo: ResourceMetaInfo
  ): UploadRequest {
    with(resourceMetaInfo) {
      /** [CaptureFragment] stores files in app's internal storage directory */
      val resourceFolder = File(context.filesDir, resourceFolderRelativePath)
      val outputZipFile = resourceFolder.absolutePath + ".zip"
      /** Zipping logic from: https://stackoverflow.com/a/63828765 */
      withContext(Dispatchers.IO) {
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

      return UploadRequest(
        requestUuid = UUID.randomUUID(),
        resourceMetaInfoId = resourceMetaInfoId,
        zipFile = outputZipFile,
        fileSize = File(outputZipFile).length(),
        fileOffset = 0L,
        bucketName = serverConfiguration.bucketName,
        uploadRelativeURL = "/$resourceFolderRelativePath.zip",
        isMultiPart = serverConfiguration.networkConfiguration.isMultiPart,
        nextPart = 1,
        uploadId = null,
        status = UploadStatus.PENDING,
        lastUpdatedTime = Date.from(Instant.now())
      )
    }
  }

  override suspend fun listResourceMetaInfoForParticipant(
    participantId: String
  ): List<ResourceMetaInfo> {
    return database.listResourceMetaInfoForParticipant(participantId)
  }

  override suspend fun listResourceMetaInfoInCapture(captureId: String): List<ResourceMetaInfo> {
    return database.listResourceMetaInfoInCapture(captureId)
  }

  override suspend fun syncUpload(upload: suspend (List<UploadRequest>) -> Flow<UploadResult>) {
    val uploadRequestsList =
      database.listUploadRequests(UploadStatus.UPLOADING) +
        database.listUploadRequests(UploadStatus.PENDING)
    upload(uploadRequestsList).collect { result ->
      val uploadRequest = result.uploadRequest
      val requestsPreviousStatus = uploadRequest.status
      when (result) {
        is UploadResult.Started -> {
          uploadRequest.apply {
            lastUpdatedTime = result.startTime
            fileOffset = 0
            status = UploadStatus.UPLOADING
            uploadId = result.uploadId
            nextPart = 1
          }
        }
        is UploadResult.Success -> {
          uploadRequest.apply {
            lastUpdatedTime = result.lastUploadTime
            fileOffset = uploadRequest.fileOffset + result.bytesUploaded
            nextPart = uploadRequest.nextPart + 1
          }
        }
        is UploadResult.Completed -> {
          assert(uploadRequest.fileOffset == uploadRequest.fileSize)
          uploadRequest.apply {
            lastUpdatedTime = result.completeTime
            status = UploadStatus.UPLOADED
          }
          /** Delete the zipped file as its no longer required. */
          File(uploadRequest.zipFile).delete()
        }
        is UploadResult.Failure -> {
          uploadRequest.apply {
            lastUpdatedTime = uploadRequest.lastUpdatedTime
            status = UploadStatus.FAILED
          }
        }
      }
      database.updateUploadRequest(uploadRequest)
      /** Update status of ResourceMetaInfo only when UploadRequest.status changes */
      if (requestsPreviousStatus != uploadRequest.status) {
        val resourceMetaInfo = database.getResourceMetaInfo(uploadRequest.resourceMetaInfoId)
        resourceMetaInfo.apply { uploadStatus = uploadRequest.status }
        database.updateResourceMetaInfo(resourceMetaInfo)
      }
    }
  }

  override suspend fun getUploadRequest(resourceMetaInfoId: String): UploadRequest? {
    TODO("Not yet implemented")
  }

  override suspend fun getCaptureInfo(captureId: String): CaptureInfo {
    return database.getCaptureInfo(captureId)
  }

  override suspend fun deleteDataInCapture(captureId: String): Boolean {
    val captureInfo =
      try {
        getCaptureInfo(captureId)
      } catch (e: ResourceNotFoundException) {
        null
      } ?: return true

    // Step 1: Delete db records
    database.deleteRecordsInCapture(captureId)
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

  companion object {
    /** File format for any sensor is taken from [captureSettings.fileTypeMap]. */
    private fun resourceMetaInfoFileType(sensorType: SensorType, captureInfo: CaptureInfo): String {
      return when (sensorType) {
        SensorType.CAMERA -> captureInfo.captureSettings!!.fileTypeMap[sensorType]!!
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
