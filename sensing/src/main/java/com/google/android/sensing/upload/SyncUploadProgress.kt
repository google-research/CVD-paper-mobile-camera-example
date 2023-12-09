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

import java.time.OffsetDateTime

open class SyncUploadProgress {
  val timestamp: OffsetDateTime = OffsetDateTime.now()

  data class Started(val totalRequests: Int) : SyncUploadProgress()

  data class InProgress(
    val totalRequests: Int,
    val completedRequests: Int,
    val currentTotalBytes: Long,
    val currentCompletedBytes: Long,
  ) : SyncUploadProgress()

  data class Completed(val totalRequests: Int) : SyncUploadProgress()

  data class Failed(val exceptions: Exception) : SyncUploadProgress()

  object NoOp : SyncUploadProgress()
}
