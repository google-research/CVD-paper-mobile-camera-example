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
    workManagerSyncConfiguration: WorkManagerSyncConfiguration =
      defaultWorkManagerSyncConfiguration,
  ): Flow<SyncUploadState> {
    WorkManager.getInstance(context)
      .enqueueUniqueWork(
        oneTimeWorkUniqueName,
        ExistingWorkPolicy.KEEP,
        createOneTimeWorkRequest(workManagerSyncConfiguration)
      )
    return getUploadProgressFlow(context, oneTimeWorkUniqueName)
  }

  @PublishedApi
  internal fun createOneTimeWorkRequest(
    workManagerSyncConfiguration: WorkManagerSyncConfiguration
  ): OneTimeWorkRequest {
    val oneTimeWorkRequestBuilder = OneTimeWorkRequest.Builder(SensorDataUploadWorker::class.java)
    workManagerSyncConfiguration.retryConfiguration?.let {
      oneTimeWorkRequestBuilder.setBackoffCriteria(
        it.backoffCriteria.backoffPolicy,
        it.backoffCriteria.backoffDelay,
        it.backoffCriteria.timeUnit
      )
      oneTimeWorkRequestBuilder.setInputData(
        Data.Builder().putInt(MAX_RETRIES_ALLOWED, it.maxRetries).build()
      )
    }
    oneTimeWorkRequestBuilder.setConstraints(workManagerSyncConfiguration.syncConstraints)
    return oneTimeWorkRequestBuilder.build()
  }

  fun periodicSyncUpload(
    context: Context,
    workManagerSyncConfiguration: WorkManagerSyncConfiguration =
      defaultWorkManagerSyncConfiguration,
  ): Flow<SyncUploadState> {
    WorkManager.getInstance(context)
      .enqueueUniquePeriodicWork(
        periodicWorkUniqueName,
        ExistingPeriodicWorkPolicy.KEEP,
        createPeriodicWorkRequest(workManagerSyncConfiguration)
      )
    return getUploadProgressFlow(context, periodicWorkUniqueName)
  }

  @PublishedApi
  internal fun createPeriodicWorkRequest(
    workManagerSyncConfiguration: WorkManagerSyncConfiguration
  ): PeriodicWorkRequest {
    val periodicWorkRequestBuilder =
      PeriodicWorkRequest.Builder(
          SensorDataUploadWorker::class.java,
          workManagerSyncConfiguration.repeat.interval,
          workManagerSyncConfiguration.repeat.timeUnit
        )
        .setConstraints(workManagerSyncConfiguration.syncConstraints)

    workManagerSyncConfiguration.retryConfiguration?.let {
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
          .takeIf { it.keyValueMap.isNotEmpty() && it.hasKeyWithValueOfType<String>("StateType") }
          ?.let {
            val state = it.getString("StateType")!!
            val stateData = it.getString("State")
            SensingUploadSync.gson.fromJson(stateData, Class.forName(state)) as SyncUploadState
          }
      }
}
