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
import com.google.android.sensing.model.RequestStatus
import com.google.android.sensing.model.UploadResult
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class SensingSynchronizer(
  private val sensingEngine: SensingEngine,
  private val uploader: Uploader,
  private val uploadResultProcessor: UploadResultProcessor
) {

  /**
   * Sync Upload API. It fetches [UploadRequest]s, invokes [uploader], collects [UploadResult]s
   * processes them using [UploadResult]s by [uploadResultProcessor]. At each stage it emits
   * [SyncUploadState]s.
   *
   * TODO Upload until there is no upload requests in the database. Use UploadRequestPublisher
   *
   * TODO Persist the terminal SyncUploadState due to this
   * [issue](https://github.com/google/android-fhir/issues/2119).
   */
  suspend fun synchronize(): Flow<SyncUploadState> = flow {
    val uploadRequestList =
      (sensingEngine.listUploadRequest(status = RequestStatus.UPLOADING) +
        sensingEngine.listUploadRequest(status = RequestStatus.PENDING))

    var lastSyncUploadState = SyncUploadState.Started(uploadRequestList.size) as SyncUploadState
    emit(lastSyncUploadState)
    if (uploadRequestList.isNotEmpty()) {
      // Following line is a workaround to calculate new states based on previous "InProgress"
      // states.
      lastSyncUploadState =
        SyncUploadState.InProgress(
          totalRequests = uploadRequestList.size,
          completedRequests = 0,
          currentTotalBytes = 0,
          currentCompletedBytes = 0
        )
      // https://stackoverflow.com/questions/60761812/unable-to-execute-code-after-kotlin-flow-collect
      uploader.upload(uploadRequestList).collect {
        uploadResultProcessor.process(it)
        val newSyncUploadState =
          calculateSyncUploadState(lastSyncUploadState as SyncUploadState.InProgress, it)
        emit(newSyncUploadState)
        lastSyncUploadState = newSyncUploadState
        if (newSyncUploadState.isTerminalState()) awaitCancellation()
      }
    }
  }

  private fun calculateSyncUploadState(
    lastSyncUploadState: SyncUploadState.InProgress,
    uploadResult: UploadResult
  ): SyncUploadState {
    /**
     * The last states cannot be terminal states like Completed, Failed, NoOp. Hence the only
     * possible last states are [SyncUploadState.Started] & [SyncUploadState.InProgress].
     */
    with(lastSyncUploadState) {
      return when (uploadResult) {
        is UploadResult.Started -> {
          SyncUploadState.InProgress(
            totalRequests = totalRequests,
            completedRequests = completedRequests,
            currentTotalBytes = uploadResult.uploadRequest.fileSize,
            currentCompletedBytes = 0
          )
        }
        is UploadResult.Success ->
          SyncUploadState.InProgress(
            totalRequests = totalRequests,
            completedRequests = completedRequests,
            currentTotalBytes = currentTotalBytes,
            currentCompletedBytes = currentCompletedBytes + uploadResult.bytesUploaded
          )
        is UploadResult.Completed ->
          if (completedRequests + 1 == totalRequests) SyncUploadState.Completed(totalRequests)
          else
            SyncUploadState.InProgress(
              totalRequests = totalRequests,
              completedRequests = completedRequests + 1,
              currentTotalBytes = currentTotalBytes,
              currentCompletedBytes = currentCompletedBytes
            )
        is UploadResult.Failure -> SyncUploadState.Failed(exception = uploadResult.uploadError)
      }
    }
  }
}

internal fun SyncUploadState.isTerminalState() =
  (this is SyncUploadState.Completed ||
    this is SyncUploadState.Failed ||
    this is SyncUploadState.NoOp)
