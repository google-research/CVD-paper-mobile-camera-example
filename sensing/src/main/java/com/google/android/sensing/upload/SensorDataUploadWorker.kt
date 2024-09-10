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
import com.google.gson.ExclusionStrategy
import com.google.gson.FieldAttributes
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

/** A WorkManager Worker that handles onetime and periodic requests to upload. */
class SensorDataUploadWorker(appContext: Context, workerParams: WorkerParameters) :
  CoroutineWorker(appContext, workerParams) {

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
      SensingSynchronizer.getInstance(applicationContext)?.let {
        it
          .synchronize()
          .onEach {
            setProgress(buildWorkData(it))
            if (it is SyncUploadState.Failed) {
              delay(20) // So that final progress is also received on the application's end.
              failed = true
              println("Synchronization Exception: ${it.exception}")
              Timber.e("Synchronization Exception: ${it.exception}")
            }
          }
          .firstOrNull { it is SyncUploadState.Completed }
      }
        ?: throw SynchronizerException(
          "Synchronizer instance not created! Have you configured the server correctly ?"
        )
      return if (failed) Result.retry() else Result.success()
    } catch (exception: Exception) {
      setProgress(buildWorkData(SyncUploadState.Failed(null, exception)))
      delay(20) // So that final progress is also received on the application's end.
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
