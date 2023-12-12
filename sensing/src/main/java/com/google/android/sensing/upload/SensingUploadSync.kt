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
import androidx.lifecycle.asFlow
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.hasKeyWithValueOfType
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.time.OffsetDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.mapNotNull

object SensingUploadSync {

  val gson: Gson =
    GsonBuilder()
      .registerTypeAdapter(OffsetDateTime::class.java, OffsetDateTimeTypeAdapter().nullSafe())
      .create()

  private val oneTimeWorkUniqueName = "Unique_" + SensorDataUploadWorker::class.java.name
  private val periodicWorkUniqueName = "Periodic_" + SensorDataUploadWorker::class.java.name

  fun oneTimeSyncUpload(
    context: Context,
    retryConfiguration: RetryConfiguration = defaultRetryConfiguration,
  ): Flow<SyncUploadState> {
    WorkManager.getInstance(context)
      .enqueueUniqueWork(
        oneTimeWorkUniqueName,
        ExistingWorkPolicy.KEEP,
        createOneTimeWorkRequest(retryConfiguration)
      )
    return getUploadProgressFlow(context, oneTimeWorkUniqueName)
  }

  @PublishedApi
  internal fun createOneTimeWorkRequest(
    retryConfiguration: RetryConfiguration?
  ): OneTimeWorkRequest {
    val oneTimeWorkRequestBuilder = OneTimeWorkRequest.Builder(SensorDataUploadWorker::class.java)
    retryConfiguration?.let {
      oneTimeWorkRequestBuilder.setBackoffCriteria(
        it.backoffCriteria.backoffPolicy,
        it.backoffCriteria.backoffDelay,
        it.backoffCriteria.timeUnit
      )
      oneTimeWorkRequestBuilder.setInputData(
        Data.Builder().putInt(MAX_RETRIES_ALLOWED, it.maxRetries).build()
      )
    }
    return oneTimeWorkRequestBuilder.build()
  }

  fun periodicSyncUpload(
    context: Context,
    periodicSyncConfiguration: PeriodicSyncConfiguration = defaultPeriodicSyncConfiguration,
  ): Flow<SyncUploadState> {
    WorkManager.getInstance(context)
      .enqueueUniquePeriodicWork(
        periodicWorkUniqueName,
        ExistingPeriodicWorkPolicy.KEEP,
        createPeriodicWorkRequest(periodicSyncConfiguration)
      )
    return getUploadProgressFlow(context, periodicWorkUniqueName)
  }

  @PublishedApi
  internal fun createPeriodicWorkRequest(
    periodicSyncConfiguration: PeriodicSyncConfiguration
  ): PeriodicWorkRequest {
    val periodicWorkRequestBuilder =
      PeriodicWorkRequest.Builder(
          SensorDataUploadWorker::class.java,
          periodicSyncConfiguration.repeat.interval,
          periodicSyncConfiguration.repeat.timeUnit
        )
        .setConstraints(periodicSyncConfiguration.syncConstraints)

    periodicSyncConfiguration.retryConfiguration?.let {
      periodicWorkRequestBuilder.setBackoffCriteria(
        it.backoffCriteria.backoffPolicy,
        it.backoffCriteria.backoffDelay,
        it.backoffCriteria.timeUnit
      )
      periodicWorkRequestBuilder.setInputData(
        Data.Builder().putInt(MAX_RETRIES_ALLOWED, it.maxRetries).build()
      )
    }
    return periodicWorkRequestBuilder.build()
  }

  private fun getUploadProgressFlow(
    context: Context,
    uniqueWorkerName: String
  ): Flow<SyncUploadState> =
    WorkManager.getInstance(context)
      .getWorkInfosForUniqueWorkLiveData(uniqueWorkerName)
      .asFlow()
      .flatMapConcat { it.asFlow() }
      .mapNotNull {
        it.progress
          .takeIf {
            it.keyValueMap.isNotEmpty() && it.hasKeyWithValueOfType<String>("ProgressType")
          }
          ?.let {
            val state = it.getString("ProgressType")!!
            val stateData = it.getString("Progress")
            SensingUploadSync.gson.fromJson(stateData, Class.forName(state)) as SyncUploadState
          }
      }
}
