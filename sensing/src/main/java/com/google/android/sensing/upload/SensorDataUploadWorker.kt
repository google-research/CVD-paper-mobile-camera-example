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
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.android.sensing.SensingEngineProvider
import com.google.gson.ExclusionStrategy
import com.google.gson.FieldAttributes
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicBoolean
import timber.log.Timber

/** A WorkManager Worker that handles onetime and periodic requests to upload. */
class SensorDataUploadWorker(appContext: Context, workerParams: WorkerParameters) :
  CoroutineWorker(appContext, workerParams) {

  // Each new upload work will use a new instance of uploader
  private val uploader = Uploader(SensingEngineProvider.getBlobStoreService())

  private val sensingEngine = SensingEngineProvider.getOrCreateSensingEngine(applicationContext)

  private val uploadResultProcessor = DefaultUploadResultProcessor(sensingEngine)
  private val uploadRequestFetcher = DefaultUploadRequestFetcher(sensingEngine)

  private val gson =
    GsonBuilder()
      .registerTypeAdapter(OffsetDateTime::class.java, OffsetDateTimeTypeAdapter().nullSafe())
      .setExclusionStrategies(StateExclusionStrategy())
      .create()

  private fun tryAcquiringLock(): Boolean {
    return isAnyWorkerSynchronizing.compareAndSet(false, true)
  }

  private fun releaseLock(): Boolean {
    return isAnyWorkerSynchronizing.compareAndSet(true, false)
  }

  override suspend fun doWork(): Result {
    if (runAttemptCount == inputData.getInt(MAX_RETRIES_ALLOWED, 0)) return Result.failure()
    if (!tryAcquiringLock()) {
      return Result.success(buildWorkData(SyncUploadState.NoOp))
    }
    try {
      var failed = false
      SensingSynchronizer(
          uploadRequestFetcher = uploadRequestFetcher,
          uploader = uploader,
          uploadResultProcessor = uploadResultProcessor
        )
        .synchronize()
        .collect {
          setProgress(buildWorkData(it))
          if (it is SyncUploadState.Failed) {
            failed = true
            Timber.e("Synchronization Exception: ${it.exception}")
          }
        }
      return if (failed) Result.retry() else Result.success()
    } catch (exception: Exception) {
      setProgress(buildWorkData(SyncUploadState.Failed(null, exception)))
      Timber.e("Synchronization Exception: $exception")
      return Result.retry()
    } finally {
      releaseLock()
    }
  }

  private fun buildWorkData(syncUploadState: SyncUploadState) =
    workDataOf(
      "StateType" to syncUploadState::class.java.name,
      "State" to gson.toJson(syncUploadState)
    )

  /**
   * Exclusion strategy for [Gson] that handles field exclusions for [SyncUploadState] returned by
   * FhirEngine. It should skip serializing the exceptions to avoid exceeding WorkManager WorkData
   * limit
   *
   * @see <a
   * href="https://github.com/google/android-fhir/issues/707">https://github.com/google/android-fhir/issues/707</a>
   */
  internal class StateExclusionStrategy : ExclusionStrategy {
    override fun shouldSkipField(field: FieldAttributes) = field.name.equals("exception")

    override fun shouldSkipClass(clazz: Class<*>?) = false
  }

  companion object {
    private val isAnyWorkerSynchronizing = AtomicBoolean(false)
  }
}
