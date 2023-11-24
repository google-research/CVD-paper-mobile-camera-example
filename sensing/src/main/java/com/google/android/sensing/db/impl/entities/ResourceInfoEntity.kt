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

package com.google.android.sensing.db.impl.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.android.sensing.model.RequestStatus

@Entity(
  indices =
    [
      Index(value = ["resourceFolderRelativePath"], unique = true),
      Index(value = ["resourceInfoId"], unique = true),
      Index(value = ["participantId"]),
      Index(value = ["captureTitle"])
    ],
  foreignKeys =
    [
      ForeignKey(
        entity = CaptureInfoEntity::class,
        parentColumns = ["captureId"],
        childColumns = ["captureId"],
        onDelete = ForeignKey.CASCADE
      )
    ]
)
/** Information about the resource collected per capture. This is not involved in uploading. */
internal data class ResourceInfoEntity(
  @PrimaryKey(autoGenerate = true) val id: Long,

  /** Unique Id of this record. */
  val resourceInfoId: String,

  /** Id of the capture that created this record. */
  val captureId: String,

  /** Participant for which the capture created this record. */
  val participantId: String,

  /** Title of the capture that creates this record. */
  val captureTitle: String,

  /** Resource extension */
  val fileType: String,

  /** Relative android folder location of the captured data from a sensor. */
  val resourceFolderRelativePath: String,

  /** Absolute and immutable upload url. */
  val uploadURL: String,

  /** Upload status. */
  val status: RequestStatus,
)
