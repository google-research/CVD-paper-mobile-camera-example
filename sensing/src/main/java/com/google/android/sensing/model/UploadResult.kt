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

package com.google.android.sensing.model

import java.util.Date

sealed class UploadResult {
  abstract val uploadRequest: UploadRequest

  data class Started(
    override val uploadRequest: UploadRequest,
    val startTime: Date,
    val uploadId: String,
  ) : UploadResult()

  data class Success(
    override val uploadRequest: UploadRequest,
    val bytesUploaded: Long,
    val lastUploadTime: Date,
  ) : UploadResult()

  data class Completed(override val uploadRequest: UploadRequest, val completeTime: Date) :
    UploadResult()

  data class Failure(override val uploadRequest: UploadRequest, val uploadError: Exception) :
    UploadResult()
}
