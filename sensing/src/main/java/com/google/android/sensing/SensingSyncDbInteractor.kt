/*
 * Copyright 2024 Google LLC
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

import android.content.Context
import com.google.android.sensing.db.Database
import com.google.android.sensing.model.RequestStatus
import com.google.android.sensing.model.ResourceInfo
import com.google.android.sensing.model.UploadRequest
import com.google.android.sensing.model.UploadResult
import java.io.File

/** Interface defining interactions with a database for sensor data synchronization. */
internal interface SensingSyncDbInteractor {
  /**
   * Updates an existing ResourceInfo entry in the database.
   *
   * @param resourceInfo The ResourceInfo object containing the data to be updated.
   */
  suspend fun updateResourceInfo(resourceInfo: ResourceInfo)

  /**
   * Updates the state of an UploadRequest entry in the database. This might involve changing the
   * status (pending, in-progress, completed, failed), or other relevant details.
   *
   * @param uploadRequest The UploadRequest object to be updated.
   */
  suspend fun updateUploadRequest(uploadRequest: UploadRequest)

  /**
   * Retrieves a list of UploadRequest objects from the database that are marked as ready for
   * upload.
   *
   * @return A list of UploadRequest objects pending upload.
   */
  suspend fun fetchUploadRequestsToUpload(): List<UploadRequest>

  /**
   * Processes the result of an upload operation and updates the database accordingly. This may
   * include:
   * * Marking an upload request as successful or failed.
   * * Updating progress information for partial or multi-part uploads.
   * * Recording timestamps for synchronization purposes.
   *
   * @param uploadResult The UploadResult object containing details about the upload outcome.
   */
  suspend fun processUploadResult(uploadResult: UploadResult)

  companion object {
    @Volatile private var instance: SensingSyncDbInteractor? = null

    fun getInstance(context: Context) =
      instance
        ?: synchronized(this) {
          instance
            ?: run {
                val appContext = context.applicationContext
                val sensingEngineConfiguration =
                  if (appContext is SensingEngineConfiguration.Provider) {
                    appContext.getSensingEngineConfiguration()
                  } else SensingEngineConfiguration()
                with(sensingEngineConfiguration) {
                  val database = Database.getInstance(context, databaseConfiguration)
                  SensingSyncDbInteractorImpl(database)
                }
              }
              .also { instance = it }
        }
  }
}

internal class SensingSyncDbInteractorImpl(private val database: Database) :
  SensingSyncDbInteractor {
  override suspend fun updateResourceInfo(resourceInfo: ResourceInfo) {
    database.updateResourceInfo(resourceInfo)
  }

  override suspend fun updateUploadRequest(uploadRequest: UploadRequest) {
    return database.updateUploadRequest(uploadRequest)
  }

  override suspend fun fetchUploadRequestsToUpload(): List<UploadRequest> {
    return (database.listUploadRequests(status = RequestStatus.UPLOADING) +
      database.listUploadRequests(status = RequestStatus.PENDING))
  }

  override suspend fun processUploadResult(uploadResult: UploadResult) {
    val uploadRequest = uploadResult.uploadRequest
    val requestsPreviousStatus = uploadRequest.status
    when (uploadResult) {
      is UploadResult.Started -> {
        uploadRequest.apply {
          lastUpdatedTime = uploadResult.startTime
          fileOffset = 0
          status = RequestStatus.UPLOADING
          uploadId = uploadResult.uploadId
          nextPart = 1
        }
      }
      is UploadResult.Success -> {
        uploadRequest.apply {
          lastUpdatedTime = uploadResult.lastUploadTime
          fileOffset = uploadRequest.fileOffset + uploadResult.bytesUploaded
          nextPart = uploadRequest.nextPart + 1
        }
      }
      is UploadResult.Completed -> {
        assert(uploadRequest.fileOffset == uploadRequest.fileSize)
        uploadRequest.apply {
          lastUpdatedTime = uploadResult.completeTime
          status = RequestStatus.UPLOADED
        }
        /** Delete the zipped file as its no longer required. */
        File(uploadRequest.zipFile).delete()
      }
      is UploadResult.Failure -> {
        uploadRequest.apply {
          lastUpdatedTime = uploadRequest.lastUpdatedTime
          status = RequestStatus.FAILED
        }
      }
    }
    updateUploadRequest(uploadRequest)
    /** Update status of ResourceInfo only when UploadRequest.status changes */
    if (requestsPreviousStatus != uploadRequest.status) {
      val resourceInfo = database.getResourceInfo(uploadRequest.resourceInfoId)!!
      resourceInfo.apply { status = uploadRequest.status }
      updateResourceInfo(resourceInfo)
    }
  }
}
