package com.google.android.sensory.sensing_sdk

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import java.util.concurrent.TimeUnit

/** Constant for the max number of retries in case of sync failure */
@PublishedApi internal const val MAX_RETRIES_ALLOWED = "max_retires"

val defaultRetryConfiguration =
  RetryConfiguration(BackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS), 3)

val defaultPeriodicSyncConfiguration =
  PeriodicSyncConfiguration(
    syncConstraints = Constraints.Builder().build(),
    repeat = RepeatInterval(interval = 15, timeUnit = TimeUnit.MINUTES),
    retryConfiguration = defaultRetryConfiguration
  )

/** Configuration for period synchronisation */
class PeriodicSyncConfiguration(
  /**
   * Constraints that specify the requirements needed before the synchronisation is triggered. E.g.
   * network type (Wifi, 3G etc), the device should be charging etc.
   */
  val syncConstraints: Constraints = Constraints.Builder().build(),

  /**
   * The interval at which the sync should be triggered in. It must be greater than or equal to
   * [androidx.work.PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS]
   */
  val repeat: RepeatInterval,

  /** Configuration for synchronization retry */
  val retryConfiguration: RetryConfiguration? = defaultRetryConfiguration,
)

data class RepeatInterval(
  /** The interval at which the sync should be triggered in */
  val interval: Long,
  /** The time unit for the repeat interval */
  val timeUnit: TimeUnit,
)

/** Configuration for synchronization retry */
data class RetryConfiguration(
  /**
   * The criteria to retry failed synchronization work based on
   * [androidx.work.WorkRequest.Builder.setBackoffCriteria]
   */
  val backoffCriteria: BackoffCriteria,

  /** Maximum retries for a failing [SensorDataUploadWorker] */
  val maxRetries: Int,
)

/**
 * The criteria for [SensorDataUploadWorker] failure retry based on
 * [androidx.work.WorkRequest.Builder.setBackoffCriteria]
 */
data class BackoffCriteria(
  /** Backoff policy [androidx.work.BackoffPolicy] */
  val backoffPolicy: BackoffPolicy,

  /**
   * Backoff delay for each retry attempt. Check
   * [androidx.work.PeriodicWorkRequest.MIN_BACKOFF_MILLIS] and
   * [androidx.work.PeriodicWorkRequest.MAX_BACKOFF_MILLIS] for the min-max supported values
   */
  val backoffDelay: Long,

  /** The time unit for [backoffDelay] */
  val timeUnit: TimeUnit,
)

data class UploadConfiguration(
  val HOST: String,
  val ACCESS_HOST: String,
  val bucketName: String,
  val user: String,
  val password: String,
) {
  fun getBlobStorageAccessURL() = "$ACCESS_HOST/$bucketName"
}