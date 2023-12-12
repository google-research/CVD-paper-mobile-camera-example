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

import java.lang.Exception

/**
 * Progress model is emitted by [SensingEngine] to tell the sync module about the upload progress.
 * We use this in engine and [SyncUploadState] in sync module respectively to follow separation of
 * concerns.
 */
data class SyncUploadProgress(
  val totalRequests: Int,
  val completedRequests: Int = 0,
  val currentTotalBytes: Long = 0,
  val currentCompletedBytes: Long = 0,
  val uploadError: Exception? = null,
)
