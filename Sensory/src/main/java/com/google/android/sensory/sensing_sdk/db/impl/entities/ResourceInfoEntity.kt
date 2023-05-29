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

package com.google.android.sensory.sensing_sdk.db.impl.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.android.sensory.sensing_sdk.model.CaptureType
import com.google.android.sensory.sensing_sdk.model.RequestStatus

@Entity(
  indices =
    [
      // Index(value = ["resourceFolderPath"], unique = true),
      Index(value = ["resourceInfoId"], unique = true),
      Index(value = ["captureId"]),
      Index(value = ["participantId"])
    ]
)
/** Information about the resource collected per capture. This is not involved in uploading. */
internal data class ResourceInfoEntity(
  @PrimaryKey(autoGenerate = true) val id: Long,
  val resourceInfoId: String,
  val captureId: String,
  val participantId: String,
  val captureType: CaptureType,
  val title: String,
  val fileType: String,
  val resourceFolderPath: String,
  /** uploadUrl should be known at instance creation and not changed post partial uploading. */
  val uploadURL: String,
  val status: RequestStatus,
)
