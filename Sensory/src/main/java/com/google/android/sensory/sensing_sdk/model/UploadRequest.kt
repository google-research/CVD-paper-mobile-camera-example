/*
 * Copyright 2022 Google LLC
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

package com.google.android.sensory.sensing_sdk.model

import java.util.Date
import java.util.UUID

/** Data class equivalent to UploadRequestEntity for usage outside database. */
data class UploadRequest(
  val requestUuid: UUID,
  val resourceInfoId: String,
  val zipFile: String,
  val fileSize: Long,
  val uploadURL: String,
  var lastUpdatedTime: Date,
  var bytesUploaded: Long,
  var status: RequestStatus,
  var nextPart: Int,
  var uploadId: String? = null,
)
