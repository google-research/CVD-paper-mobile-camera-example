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

import com.google.android.sensing.SensingEngine
import com.google.android.sensing.model.UploadResult
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.flow

class SensingSynchronizer(
  private val sensingEngine: SensingEngine,
  private val uploader: Uploader
) {

  private val _syncState = MutableSharedFlow<SyncUploadState>()
  val syncState: SharedFlow<SyncUploadState> = _syncState

  private val uploadResultProcessor =
    object : UploadResultProcessor {
      override suspend fun process(uploadResult: UploadResult) {}
    }

  suspend fun synchronizer(): Flow<SyncUploadState> = flow {
    emit(SyncUploadState.Started)
    sensingEngine.syncUpload(uploader::upload).cancellable().collect {
      val state = calculateSyncStateForGivenProgress(it)
      emit(state)
      if (state is SyncUploadState.Failed) awaitCancellation()
    }
    emit(SyncUploadState.Completed)
  }

  private fun calculateSyncStateForGivenProgress(
    syncUploadProgress: SyncUploadProgress
  ): SyncUploadState {
    with(syncUploadProgress) {
      return if (uploadError != null) SyncUploadState.Failed(uploadError)
      else
        SyncUploadState.InProgress(
          totalRequests = totalRequests,
          completedRequests = completedRequests,
          currentTotalBytes = currentTotalBytes,
          currentCompletedBytes = currentCompletedBytes,
        )
    }
  }
}
