/*
 * Copyright 2023-2024 Google LLC
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
import com.google.android.sensing.model.UploadRequest
import com.google.android.sensing.model.UploadResult
import com.google.common.collect.HashMultimap
import java.io.File
import java.io.FileInputStream
import java.time.Instant
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber

interface Uploader {
  fun upload(uploadRequest: UploadRequest): Flow<UploadResult>

  // https://www.baeldung.com/kotlin/singleton-classes#1-companion-object
  companion object {
    @Volatile private var instance: Uploader? = null
    fun getInstance(context: Context) =
      instance
        ?: synchronized(this) {
          instance
            ?: run {
              BlobstoreService.getInstance(context)?.let { UploaderImpl(it).also { instance = it } }
            }
        }
  }
}

/**
 * Processes upload requests and uploads the data referenced in chunks. Ideally we would want the
 * uploader to figure out [uploadPartSizeInBytes] based on network strength
 */
private class UploaderImpl(private val blobstoreService: BlobstoreService) : Uploader {
  /**
   * TODO: Ideally this should not be hardcode 6MB (6291456L) bytes as part size. Instead this
   * should be a function of network strength. Note: Min upload part size of MinioAsyncClient is
   * 5MB.
   */
  private val uploadPartSizeInBytes = 6291456L
  private var minPartSizeInBytes = 5242880L // 5MB
  override fun upload(uploadRequest: UploadRequest): Flow<UploadResult> =
    flow {
        try {
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
            emit(
              UploadResult.Started(
                uploadRequest,
                Date.from(Instant.now()),
                uploadRequest.uploadId!!
              )
            )
          }
          FileInputStream(File(uploadRequest.zipFile)).use { dataStream ->
            // https://stackoverflow.com/a/28804975
            dataStream.channel.position(uploadRequest.fileOffset)
            var bytesUploaded = uploadRequest.fileOffset
            // Upload until last part
            while (uploadRequest.isMultiPart &&
              uploadRequest.fileSize - bytesUploaded >=
                uploadPartSizeInBytes + minPartSizeInBytes) {
              val chunkSize = uploadPartSizeInBytes
              val buffer = ByteArray(chunkSize.toInt())
              dataStream.read(buffer)
              Timber.d("Uploading part ${uploadRequest.nextPart}..")
              uploadPart(uploadRequest, buffer, chunkSize)
              bytesUploaded += chunkSize
              emit(UploadResult.Success(uploadRequest, chunkSize, Date.from(Instant.now())))
            }
            val chunkSize = uploadRequest.fileSize - bytesUploaded
            val buffer = ByteArray(chunkSize.toInt())
            dataStream.read(buffer)
            Timber.d("Uploading part ${uploadRequest.nextPart}..")
            uploadPart(uploadRequest, buffer, chunkSize)
            emit(UploadResult.Success(uploadRequest, chunkSize, Date.from(Instant.now())))
            emit(mergeMultipartUpload(uploadRequest))
            Timber.d("File Uploaded and Merged")
          }
        } catch (e: Exception) {
          emit(UploadResult.Failure(uploadRequest, e))
        }
      }
      .flowOn(Dispatchers.IO)

  private fun mergeMultipartUpload(uploadRequest: UploadRequest): UploadResult {
    blobstoreService.mergeMultipartUpload(
      uploadRequest.bucketName,
      null,
      uploadRequest.uploadRelativeURL,
      uploadRequest.uploadId!!,
      uploadRequest.parts,
      null,
      null
    )
    return UploadResult.Completed(uploadRequest, Date.from(Instant.now()))
  }

  private fun uploadPart(
    uploadRequest: UploadRequest,
    data: ByteArray,
    chunkSize: Long,
  ) {
    val response =
      blobstoreService.uploadFilePart(
        uploadRequest.bucketName,
        null,
        uploadRequest.uploadRelativeURL,
        data,
        chunkSize,
        uploadRequest.uploadId!!,
        uploadRequest.nextPart,
        null,
        null
      )
    uploadRequest.parts.add(Part(response.partNumber, response.etag))
  }
}
