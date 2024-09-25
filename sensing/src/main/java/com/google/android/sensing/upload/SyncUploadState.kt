/*
 * Copyright 2023-2024 Google LLC
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

import java.time.OffsetDateTime

/** Used to define different sync states by the sync module. */
open class SyncUploadState {
  val timestamp: OffsetDateTime = OffsetDateTime.now()

  /** The sync has started but nothing has progressed so far. */
  data class Started(val initialTotalRequests: Int) : SyncUploadState()

  /**
   * There is some progress represented by this state. It could be resource-level progress in case
   * the resource is divided into parts to upload. This includes [currentRequestTotalBytes] &
   * [currentRequestCompletedBytes]. This state can also be used to indicate a change in
   * [currentTotalRequests] being processed. This is possible when new [UploadRequest]s are created
   * while current batch of requests are being processed.
   *
   * TODO Add a state that helps to distinguish between different batches of upload.
   */
  data class InProgress(
    val currentTotalRequests: Int,
    val completedRequests: Int = 0,
    val currentRequestTotalBytes: Long = 0,
    val currentRequestCompletedBytes: Long = 0,
  ) : SyncUploadState()

  /** The full sync has completed and there are are no further requests to be processed. */
  data class Completed(val totalRequests: Int) : SyncUploadState()

  /** The sync failed for some [exception]. */
  data class Failed(
    val resourceInfoId: String?,
    val exceptionMessage: String,
    val stacktrace: String
  ) : SyncUploadState()

  /**
   * This state indicates no operation. This is currently used to indicate that user's request to
   * sync has no operation as one of the worker is already synchronizing.
   */
  object NoOp : SyncUploadState()
}
