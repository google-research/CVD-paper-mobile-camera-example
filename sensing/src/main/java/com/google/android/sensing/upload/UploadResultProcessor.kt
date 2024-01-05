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

import com.google.android.sensing.SensingEngine
import com.google.android.sensing.model.UploadRequestStatus
import com.google.android.sensing.model.UploadResult
import java.io.File

/**
 * Responsible for processing [UploadResult]s. Examples of this would be, updating the partial
 * upload information which helps in dealing with failures, updating the lastUpdated timestamp
 * field.
 */
interface UploadResultProcessor {
  suspend fun process(uploadResult: UploadResult)
}

internal class DefaultUploadResultProcessor(private val sensingEngine: SensingEngine) :
  UploadResultProcessor {
  override suspend fun process(uploadResult: UploadResult) {
    val uploadRequest = uploadResult.uploadRequest
    val requestsPreviousStatus = uploadRequest.status
    when (uploadResult) {
      is UploadResult.Started -> {
        uploadRequest.apply {
          lastUpdatedTime = uploadResult.startTime
          fileOffset = 0
          status = UploadRequestStatus.UPLOADING
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
          status = UploadRequestStatus.UPLOADED
        }
        /** Delete the zipped file as its no longer required. */
        File(uploadRequest.zipFile).delete()
      }
      is UploadResult.Failure -> {
        uploadRequest.apply {
          lastUpdatedTime = uploadRequest.lastUpdatedTime
          status = UploadRequestStatus.FAILED
        }
      }
    }
    sensingEngine.updateUploadRequest(uploadRequest)
    /** Update status of ResourceInfo only when UploadRequest.status changes */
    if (requestsPreviousStatus != uploadRequest.status) {
      val resourceInfo = sensingEngine.getResourceInfo(uploadRequest.resourceInfoId)!!
      resourceInfo.apply { uploadRequestStatus = uploadRequest.status }
      sensingEngine.updateResourceInfo(resourceInfo)
    }
  }
}
