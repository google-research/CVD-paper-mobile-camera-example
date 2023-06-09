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

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.sensory.sensing_sdk.SensingEngine
import com.google.android.sensory.sensing_sdk.SensingEngineProvider
import com.google.android.sensory.sensing_sdk.ServerConfiguration
import com.google.android.sensory.sensing_sdk.model.UploadResult
import kotlinx.coroutines.flow.flow

/** A WorkManager Worker that handles onetime and periodic requests to upload. */
abstract class SensorDataUploadWorker(appContext: Context, workerParams: WorkerParameters) :
  CoroutineWorker(appContext, workerParams) {
  abstract fun getSensingEngine(): SensingEngine

  abstract fun getServerConfiguration(): ServerConfiguration

  open fun getUploader(): Uploader {
    val serverConfiguration = getServerConfiguration()
    return Uploader(
      serverConfiguration,
      SensingEngineProvider.getBlobStoreService(serverConfiguration)
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
