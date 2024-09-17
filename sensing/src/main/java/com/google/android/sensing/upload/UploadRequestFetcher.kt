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
import com.google.android.sensing.SensingEngine
import com.google.android.sensing.SensingEngineProvider
import com.google.android.sensing.model.RequestStatus
import com.google.android.sensing.model.UploadRequest

/** Responsible for fetching [UploadRequest] that are pending to be uploaded. */
interface UploadRequestFetcher {
  suspend fun fetchAll(): List<UploadRequest>
  suspend fun fetchNew(): List<UploadRequest>

  // https://www.baeldung.com/kotlin/singleton-classes#1-companion-object
  companion object {
    @Volatile private var instance: UploadRequestFetcher? = null

    fun getInstance(context: Context) =
      instance
        ?: synchronized(this) {
          instance
            ?: DefaultUploadRequestFetcher(SensingEngineProvider.getInstance(context)).also {
              instance = it
            }
        }
  }
}

private class DefaultUploadRequestFetcher(private val sensingEngine: SensingEngine) :
  UploadRequestFetcher {
  override suspend fun fetchAll(): List<UploadRequest> {
    return (sensingEngine.listUploadRequest(status = RequestStatus.UPLOADING) +
      sensingEngine.listUploadRequest(status = RequestStatus.PENDING))
  }

  override suspend fun fetchNew(): List<UploadRequest> {
    return fetchAll().filter { it.failedSyncAttempts == 0 }
  }
}
