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

package com.google.android.sensory.sensing_sdk.upload

import com.google.android.sensory.sensing_sdk.model.UploadRequest
import com.google.android.sensory.sensing_sdk.model.UploadResult
import com.google.common.collect.HashMultimap
import io.minio.ListPartsResponse
import io.minio.UploadPartResponse
import io.minio.messages.Part
import java.io.File
import java.io.FileInputStream
import java.time.Instant
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Processes upload requests and uploads the data referenced in chunks. Ideally we would want the
 * uploader to figure out [uploadPartSizeInBytes] based on network strength
 */
class Uploader(private val blobstoreService: BlobstoreService) {
  /**
   * TODO: Ideally this should not be hardcode 6MB (6291456L) bytes as part size. Instead this
   * should be a function of network strength. Note: Min upload part size of MinioAsyncClient is
   * 5MB.
   */
  private val uploadPartSizeInBytes = 6291456L
  private var minPartSizeInBytes = 5242880L // 5MB
  suspend fun upload(uploadRequestList: List<UploadRequest>): Flow<UploadResult> = flow {
    uploadRequestList.forEach { uploadRequest ->
      if (uploadRequest.uploadId.isNullOrEmpty()) {
        val headers = HashMultimap.create<String, String>()
        headers.put("Content-Type", "application/octet-stream")
        uploadRequest.uploadId =
          blobstoreService.initMultiPartUpload(
            uploadRequest.bucketName,
            null,
            uploadRequest.uploadRelativeURL,
            headers,
            null
          )
        Timber.d("UploadID: ${uploadRequest.uploadId}")
        emit(
          UploadResult.Started(uploadRequest, Date.from(Instant.now()), uploadRequest.uploadId!!)
        )
      }
      val dataStream = withContext(Dispatchers.IO) { FileInputStream(File(uploadRequest.zipFile)) }
      // https://stackoverflow.com/a/28804975
      dataStream.channel.position(uploadRequest.fileOffset)
      var bytesUploaded = uploadRequest.fileOffset
      // Upload until last part
      while (!uploadRequest.isMultiPart &&
        uploadRequest.fileSize - bytesUploaded >= uploadPartSizeInBytes + minPartSizeInBytes) {
        val chunkSize = uploadPartSizeInBytes
        val buffer = ByteArray(chunkSize.toInt())
        withContext(Dispatchers.IO) { dataStream.read(buffer) }
        Timber.d("Uploading part ${uploadRequest.nextPart}..")
        uploadPart(uploadRequest, buffer, chunkSize)
        bytesUploaded += chunkSize
        emit(UploadResult.Success(uploadRequest, chunkSize, Date.from(Instant.now())))
      }
      val chunkSize = uploadRequest.fileSize - bytesUploaded
      val buffer = ByteArray(chunkSize.toInt())
      withContext(Dispatchers.IO) { dataStream.read(buffer) }
      uploadPart(uploadRequest, buffer, chunkSize)
      emit(UploadResult.Success(uploadRequest, chunkSize, Date.from(Instant.now())))
      emit(mergeMultipartUpload(uploadRequest))
      Timber.d("File Uploaded and Merged")
    }
  }

  private fun mergeMultipartUpload(uploadRequest: UploadRequest): UploadResult {
    val parts = arrayOfNulls<Part>(1000)
    val partResult: ListPartsResponse =
      blobstoreService.listMultipart(
        uploadRequest.bucketName,
        null,
        uploadRequest.uploadRelativeURL,
        1000,
        0,
        uploadRequest.uploadId,
        null,
        null
      )
    var partNumber = 1
    for (part in partResult.result().partList()) {
      parts[partNumber - 1] = Part(partNumber, part.etag())
      partNumber++
    }
    blobstoreService.mergeMultipartUpload(
      uploadRequest.bucketName,
      null,
      uploadRequest.uploadRelativeURL,
      uploadRequest.uploadId,
      parts,
      null,
      null
    )
    return UploadResult.Completed(uploadRequest, Date.from(Instant.now()))
  }

  private fun uploadPart(
    uploadRequest: UploadRequest,
    data: ByteArray,
    chunkSize: Long,
  ): UploadPartResponse? {
    return blobstoreService.uploadFilePart(
      uploadRequest.bucketName,
      null,
      uploadRequest.uploadRelativeURL,
      data,
      chunkSize,
      uploadRequest.uploadId,
      uploadRequest.nextPart,
      null,
      null
    )
  }
}
