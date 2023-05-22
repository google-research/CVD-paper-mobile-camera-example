package com.google.android.sensory.sensing_sdk.upload
import com.google.android.sensory.sensing_sdk.model.UploadRequest
import com.google.android.sensory.sensing_sdk.model.UploadResult
import com.google.common.collect.HashMultimap
import io.minio.ListPartsResponse
import io.minio.MinioAsyncClient
import io.minio.ObjectWriteResponse
import io.minio.UploadPartResponse
import io.minio.messages.Part
import java.io.File
import java.io.FileInputStream
import java.time.Instant
import java.util.Date
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/** Processes upload requests and uploads the data referenced in chunks.
 * Ideally we would want the uploader to figure out [uploadPartSizeInBytes] based on network strength*/
class Uploader(
  private val bucketName: String,
  private val uploadPartSizeInBytes: Long,
  private val multiPartUpload: Boolean = true,
  client: MinioAsyncClient)  {
  private var minPartSizeInBytes: Long = 5242880  // 5MB
  private val blobstoreService = BlobstoreService(client)
  suspend fun upload(uploadRequestList: List<UploadRequest>): Flow<UploadResult> = flow{
    uploadRequestList.forEach{ uploadRequest ->
      if (uploadRequest.uploadId.isNullOrEmpty()) {
        val headers = HashMultimap.create<String, String>()
        headers.put("Content-Type", "application/octet-stream")
        uploadRequest.uploadId = blobstoreService.initMultiPartUpload(
          bucketName,
          null,
          uploadRequest.uploadURL,
          headers,
          null
        )
        println("UploadID: ${uploadRequest.uploadId}")
        emit(UploadResult.Started(uploadRequest, Date.from(Instant.now()), uploadRequest.uploadId!!))
      }
      val dataStream = withContext(Dispatchers.IO) {
        FileInputStream(File(uploadRequest.zipFile))
      }
      var chunkSize: Long
      if (!multiPartUpload) {
        chunkSize = uploadRequest.fileSize
        val bytes = ByteArray(chunkSize.toInt())
        withContext(Dispatchers.IO) {
          dataStream.read(bytes)
        }
        uploadPart(uploadRequest, bytes, chunkSize)
        emit(mergeMultipartUpload(uploadRequest))
        println("File Uploaded and Merged")
      }
      else{
        while (uploadRequest.bytesUploaded < uploadRequest.fileSize) {
          // If the remaining bytes after the present chunk is less than minPartSizeInBytes (~5MB),
          // let the final chunk cover all remaining bytes instead of chunkSize
          chunkSize = if (uploadRequest.fileSize - uploadRequest.bytesUploaded < uploadPartSizeInBytes + minPartSizeInBytes) {
            uploadRequest.fileSize - uploadRequest.bytesUploaded
          } else {
            // chunk size is either uploadPartSizeInBytes or the remaining bytes in the file stream
            // (as long as the chunk is greater than ~5MB)
            min(uploadPartSizeInBytes, uploadRequest.fileSize - uploadRequest.bytesUploaded)
          }
          val buffer = ByteArray(chunkSize.toInt())
          withContext(Dispatchers.IO) {
            dataStream.read(buffer)
          }
          println("Uploading part ${uploadRequest.nextPart}..")
          uploadPart(uploadRequest, buffer, chunkSize)
          emit(UploadResult.Success(uploadRequest, chunkSize, Date.from(Instant.now())))
        }
        println("Uploaded all parts. Merging all requests...")
        emit(mergeMultipartUpload(uploadRequest))
        println("Upload Merged")
      }
    }
  }
  private fun mergeMultipartUpload(uploadRequest: UploadRequest): UploadResult {
    val parts = arrayOfNulls<Part>(1000)
    val partResult: ListPartsResponse = blobstoreService.listMultipart(
      bucketName, null,
      uploadRequest.uploadURL, 1000, 0, uploadRequest.uploadId, null, null
    )
    var partNumber = 1
    for (part in partResult.result().partList()) {
      parts[partNumber - 1] = Part(partNumber, part.etag())
      partNumber++
    }
    blobstoreService.mergeMultipartUpload(
      bucketName, null, uploadRequest.uploadURL, uploadRequest.uploadId, parts,
      null, null
    )
    return UploadResult.Completed(uploadRequest, Date.from(Instant.now()))
  }
  private fun uploadPart(uploadRequest: UploadRequest, data: ByteArray, chunkSize: Long): UploadPartResponse? {
    return blobstoreService.uploadFilePart(
      bucketName,
      null,
      uploadRequest.uploadURL,
      data,
      chunkSize,
      uploadRequest.uploadId,
      uploadRequest.nextPart,
      null,
      null
    )
  }
}