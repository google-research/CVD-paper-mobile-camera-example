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
import com.google.android.sensing.model.UploadRequest
import com.google.android.sensing.model.UploadResult
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import java.io.InputStream

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
        dataStream.channel.position(uploadRequest.fileOffset)
        var bytesUploaded = uploadRequest.fileOffset

        fun readExact(stream: InputStream, target: ByteArray): Int {
          var offset = 0
          while (offset < target.size) {
            val r = stream.read(target, offset, target.size - offset)
            if (r == -1) return offset // EOF
            offset += r
          }
          return offset
        }

        // Upload until last part
        while (
          uploadRequest.isMultiPart &&
          uploadRequest.fileSize - bytesUploaded >= uploadPartSizeInBytes + minPartSizeInBytes
        ) {
          val chunkSizeLong = uploadPartSizeInBytes
          if (chunkSizeLong <= 0) break // sanity
          if (chunkSizeLong > Int.MAX_VALUE) throw IllegalArgumentException("chunk too large")

          val chunkSize = chunkSizeLong.toInt()
          val buffer = ByteArray(chunkSize)
          val readBytes = readExact(dataStream, buffer)
          if (readBytes == 0) {
            // EOF reached unexpectedly — break or throw depending on desired semantics
            Timber.w("Reached EOF before expected while uploading part ${uploadRequest.nextPart}")
            break
          }
          val toUpload = if (readBytes == buffer.size) buffer else buffer.copyOf(readBytes)

          Timber.d("Uploading part ${uploadRequest.nextPart} bytes=$readBytes ..")
          uploadPart(uploadRequest, toUpload, readBytes.toLong())

          bytesUploaded += readBytes
          emit(UploadResult.Success(uploadRequest, readBytes.toLong(), Date.from(Instant.now())))
        }

        // final chunk
        val finalChunkSizeLong = uploadRequest.fileSize - bytesUploaded
        if (finalChunkSizeLong > 0) {
          if (finalChunkSizeLong > Int.MAX_VALUE) throw IllegalArgumentException("chunk too large")
          val finalChunkSize = finalChunkSizeLong.toInt()
          val buffer = ByteArray(finalChunkSize)
          val readBytes = readExact(dataStream, buffer)
          if (readBytes > 0) {
            val toUpload = if (readBytes == buffer.size) buffer else buffer.copyOf(readBytes)
            Timber.d("Uploading final part ${uploadRequest.nextPart} bytes=$readBytes ..")
            uploadPart(uploadRequest, toUpload, readBytes.toLong())
            emit(UploadResult.Success(uploadRequest, readBytes.toLong(), Date.from(Instant.now())))
          } else {
            Timber.w("No bytes read for final chunk.")
          }
        } else {
          Timber.d("No final chunk to upload (finalChunkSize=$finalChunkSizeLong)")
        }

        // complete / merge
        emit(mergeMultipartUpload(uploadRequest))
        Timber.d("File Uploaded and Merged")
      }
    }.catch { e ->
      // safe place to emit failure — this will not violate exception transparency
      Timber.e(e, "Upload failed for request: ${uploadRequest.requestUuid}")
      emit(UploadResult.Failure(uploadRequest, e))
    }.flowOn(Dispatchers.IO)

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
