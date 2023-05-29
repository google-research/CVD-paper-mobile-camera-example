/*
 * Copyright 2022 Google LLC
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

import com.google.common.collect.Multimap
import io.minio.ListPartsResponse
import io.minio.MinioAsyncClient
import io.minio.ObjectWriteResponse
import io.minio.UploadPartResponse
import io.minio.errors.ErrorResponseException
import io.minio.errors.InsufficientDataException
import io.minio.errors.InternalException
import io.minio.errors.InvalidResponseException
import io.minio.errors.ServerException
import io.minio.errors.XmlParserException
import io.minio.messages.Part
import java.io.IOException
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.util.concurrent.ExecutionException

class BlobstoreService(client: MinioAsyncClient) : MinioAsyncClient(client) {
  fun initMultiPartUpload(
    bucket: String?,
    region: String?,
    `object`: String?,
    headers: Multimap<String?, String?>?,
    extraQueryParams: Multimap<String?, String?>?,
  ): String? {
    val response = createMultipartUploadAsync(bucket, region, `object`, headers, extraQueryParams)
    // Blocking until there is a response
    return response.get().result().uploadId()
  }

  @Throws(
    InsufficientDataException::class,
    IOException::class,
    NoSuchAlgorithmException::class,
    InvalidKeyException::class,
    XmlParserException::class,
    InternalException::class,
    ExecutionException::class,
    InterruptedException::class
  )
  fun uploadFilePart(
    bucketName: String?,
    region: String?,
    objectName: String?,
    data: Any?,
    length: Long,
    uploadId: String?,
    partNumber: Int,
    extraHeaders: Multimap<String?, String?>?,
    extraQueryParams: Multimap<String?, String?>?,
  ): UploadPartResponse? {
    return this.uploadPartAsync(
        bucketName,
        region,
        objectName,
        data,
        length,
        uploadId,
        partNumber,
        extraHeaders,
        extraQueryParams
      )
      .get()
  }

  @Throws(
    IOException::class,
    InvalidKeyException::class,
    NoSuchAlgorithmException::class,
    InsufficientDataException::class,
    ServerException::class,
    InternalException::class,
    XmlParserException::class,
    InvalidResponseException::class,
    ErrorResponseException::class,
    ExecutionException::class,
    InterruptedException::class
  )
  fun mergeMultipartUpload(
    bucketName: String?,
    region: String?,
    objectName: String?,
    uploadId: String?,
    parts: Array<Part?>?,
    extraHeaders: Multimap<String?, String?>?,
    extraQueryParams: Multimap<String?, String?>?,
  ): ObjectWriteResponse? {
    return completeMultipartUploadAsync(
        bucketName,
        region,
        objectName,
        uploadId,
        parts,
        extraHeaders,
        extraQueryParams
      )
      .get()
  }

  @Throws(
    NoSuchAlgorithmException::class,
    InsufficientDataException::class,
    IOException::class,
    InvalidKeyException::class,
    ServerException::class,
    XmlParserException::class,
    ErrorResponseException::class,
    InternalException::class,
    InvalidResponseException::class,
    ExecutionException::class,
    InterruptedException::class
  )
  fun listMultipart(
    bucketName: String,
    region: String?,
    objectName: String,
    maxParts: Int,
    partNumberMarker: Int,
    uploadId: String?,
    extraHeaders: Multimap<String?, String?>?,
    extraQueryParams: Multimap<String?, String?>?,
  ): ListPartsResponse {
    return listPartsAsync(
        bucketName,
        region,
        objectName,
        maxParts,
        partNumberMarker,
        uploadId,
        extraHeaders,
        extraQueryParams
      )
      .get()
  }
}
