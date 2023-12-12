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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

/** A WorkManager Worker that handles onetime and periodic requests to upload. */
class SensorDataUploadWorker(appContext: Context, workerParams: WorkerParameters) :
  CoroutineWorker(appContext, workerParams) {

  // Each new upload work will use a new instance of uploader
  private val uploader = Uploader(SensingEngineProvider.getBlobStoreService())

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
    if (!tryAcquiringLock()) {
      return Result.success(workDataOf("State" to SyncUploadState.NoOp::class.java))
    }
    var failed = false

    val job =
      CoroutineScope(Dispatchers.IO).launch {
        SensingSynchronizer(
            sensingEngine = SensingEngineProvider.getOrCreateSensingEngine(applicationContext),
            uploader = uploader
          )
          .synchronizer()
          .collect {
            setProgress(workDataOf("StateType" to it::class.java.name, "State" to gson.toJson(it)))
            when (it) {
              is SyncUploadState.NoOp,
              is SyncUploadState.Completed -> this@launch.cancel()
              is SyncUploadState.Failed -> {
                failed = true
                this@launch.cancel()
              }
            }
          }
      }

    // await/join is needed to collect states completely
    kotlin.runCatching { job.join() }.onFailure(Timber::w)

    releaseLock()
    return if (failed) Result.retry() else Result.success()
  }

  /**
   * Exclusion strategy for [Gson] that handles field exclusions for [SyncUploadState] returned by
   * FhirEngine. It should skip serializing the exceptions to avoid exceeding WorkManager WorkData
   * limit
   *
   * @see <a
   * href="https://github.com/google/android-fhir/issues/707">https://github.com/google/android-fhir/issues/707</a>
   */
  internal class StateExclusionStrategy : ExclusionStrategy {
    override fun shouldSkipField(field: FieldAttributes) = field.name.equals("exceptions")

    override fun shouldSkipClass(clazz: Class<*>?) = false
  }

  companion object {
    private val isAnyWorkerSynchronizing = AtomicBoolean(false)
  }
}
