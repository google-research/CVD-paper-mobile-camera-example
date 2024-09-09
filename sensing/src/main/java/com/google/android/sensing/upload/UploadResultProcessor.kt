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

package com.google.android.sensing.upload

import android.content.Context
import com.google.android.sensing.SensingEngine
import com.google.android.sensing.SensingEngineProvider
import com.google.android.sensing.model.RequestStatus
import com.google.android.sensing.model.UploadResult
import java.io.File

/**
 * Responsible for processing [UploadResult]s. Examples of this would be, updating the partial
 * upload information which helps in dealing with failures, updating the lastUpdated timestamp
 * field.
 */
interface UploadResultProcessor {
  suspend fun process(uploadResult: UploadResult)

  // https://www.baeldung.com/kotlin/singleton-classes#1-companion-object
  companion object {
    @Volatile private var instance: UploadResultProcessor? = null

    fun getInstance(context: Context) =
      instance
        ?: synchronized(this) {
          instance
            ?: DefaultUploadResultProcessor(SensingEngineProvider.getInstance(context)).also {
              instance = it
            }
        }
  }
}

private class DefaultUploadResultProcessor(private val sensingEngine: SensingEngine) :
  UploadResultProcessor {
  override suspend fun process(uploadResult: UploadResult) {
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
          failedSyncAttempts = 0
        }
      }
      is UploadResult.Success -> {
        uploadRequest.apply {
          lastUpdatedTime = uploadResult.lastUploadTime
          fileOffset = uploadRequest.fileOffset + uploadResult.bytesUploaded
          nextPart = uploadRequest.nextPart + 1
          failedSyncAttempts = 0
        }
      }
      is UploadResult.Completed -> {
        assert(uploadRequest.fileOffset == uploadRequest.fileSize)
        uploadRequest.apply {
          lastUpdatedTime = uploadResult.completeTime
          status = RequestStatus.UPLOADED
          failedSyncAttempts = 0
        }
        /** Delete the zipped file as its no longer required. */
        File(uploadRequest.zipFile).delete()
      }
      is UploadResult.Failure -> {
        uploadRequest.apply {
          lastUpdatedTime = uploadRequest.lastUpdatedTime
          failedSyncAttempts++
          status =
            if (failedSyncAttempts >= MAX_FAILED_ATTEMPTS) RequestStatus.FAILED
            else uploadRequest.status
        }
      }
    }
    sensingEngine.updateUploadRequest(uploadRequest)
    /** Update status of ResourceInfo only when UploadRequest.status changes */
    if (requestsPreviousStatus != uploadRequest.status) {
      val resourceInfo = sensingEngine.getResourceInfo(uploadRequest.resourceInfoId)!!
      resourceInfo.apply { status = uploadRequest.status }
      sensingEngine.updateResourceInfo(resourceInfo)
    }
  }

  companion object {
    const val MAX_FAILED_ATTEMPTS = 3
  }
}
