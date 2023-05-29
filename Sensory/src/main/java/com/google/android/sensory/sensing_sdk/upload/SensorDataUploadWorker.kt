package com.google.android.sensory.sensing_sdk.upload

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.sensory.sensing_sdk.SensingEngine
import com.google.android.sensory.sensing_sdk.UploadConfiguration
import com.google.android.sensory.sensing_sdk.model.UploadResult
import io.minio.MinioAsyncClient
import kotlinx.coroutines.flow.flow

abstract class SensorDataUploadWorker(appContext: Context, workerParams: WorkerParameters) :
  CoroutineWorker(appContext, workerParams) {
  abstract fun getSensingEngine(): SensingEngine

  abstract fun getUploadConfiguration(): UploadConfiguration

  /** Ideally this should not be hardcode 6MB (6291456L) bytes as part size. Instead this should be a function of network strength.
   * Note: Min upload part size of MinioAsyncClient is 5MB*/
  open fun getUploader(): Uploader {
    val uploadConfiguration = getUploadConfiguration()
    return Uploader(
      uploadConfiguration.bucketName,
      6291456L,
      true,
      MinioAsyncClient.builder()
        .endpoint(uploadConfiguration.HOST)
        .credentials(uploadConfiguration.user, uploadConfiguration.password)
        .build()
    )
  }

  override suspend fun doWork(): Result {
    var failed = false
    getSensingEngine().syncUpload { list ->
      flow {
        getUploader().upload(list).collect {
          emit(it)
          if (it is UploadResult.Failure) {
            failed = true
            return@collect
          }
        }
      }
    }
    return if (failed) Result.retry() else Result.success()
  }
}