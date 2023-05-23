package com.google.android.sensory.sensing_sdk.upload

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.google.android.sensory.sensing_sdk.MAX_RETRIES_ALLOWED
import com.google.android.sensory.sensing_sdk.PeriodicSyncConfiguration
import com.google.android.sensory.sensing_sdk.RetryConfiguration
import com.google.android.sensory.sensing_sdk.defaultPeriodicSyncConfiguration
import com.google.android.sensory.sensing_sdk.defaultRetryConfiguration

object UploadSync {
  inline fun <reified  W: SensorDataUploadWorker> enqueueUploadUniqueWork(
    context: Context,
    retryConfiguration: RetryConfiguration = defaultRetryConfiguration
  ) {
    WorkManager.getInstance(context)
      .enqueueUniqueWork(
        W::class.java.name,
        ExistingWorkPolicy.KEEP,
        createOneTimeWorkRequest(retryConfiguration, W::class.java)
      )
  }

  @PublishedApi
  internal inline fun <W : SensorDataUploadWorker> createOneTimeWorkRequest(
    retryConfiguration: RetryConfiguration?,
    clazz: Class<W>
  ): OneTimeWorkRequest {
    val oneTimeWorkRequestBuilder = OneTimeWorkRequest.Builder(clazz)
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

  inline fun <reified W: SensorDataUploadWorker> enqueueUploadPeriodicWork(
    context: Context,
    periodicSyncConfiguration: PeriodicSyncConfiguration = defaultPeriodicSyncConfiguration
  ) {
    WorkManager.getInstance(context)
      .enqueueUniquePeriodicWork(
        W::class.java.name,
        ExistingPeriodicWorkPolicy.KEEP,
        createPeriodicWorkRequest(periodicSyncConfiguration, W::class.java)
      )
  }

  @PublishedApi
  internal inline fun <W : SensorDataUploadWorker> createPeriodicWorkRequest(
    periodicSyncConfiguration: PeriodicSyncConfiguration,
    clazz: Class<W>
  ): PeriodicWorkRequest {
    val periodicWorkRequestBuilder =
      PeriodicWorkRequest.Builder(
        clazz,
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
}