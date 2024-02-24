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

package com.google.android.sensing.db.impl.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.android.sensing.model.RequestStatus
import java.time.Instant

@Entity(
  indices =
    [
      Index(value = ["localLocation"], unique = true),
      Index(value = ["externalIdentifier"]),
      Index(value = ["resourceTitle"]),
      Index(value = ["captureId"]),
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
  /** Unique Id of this record. */
  @PrimaryKey val resourceInfoId: String,

  /** Id of the capture that created this record. */
  val captureId: String,

  /** Participant for which the capture created this record. */
  val externalIdentifier: String,

  /** Android location of the captured data from a sensor. */
  val localLocation: String,

  /** Absolute and immutable remote location, eg, url. */
  val remoteLocation: String,

  /** Label to display in place of the data. */
  val resourceTitle: String,

  /** Mime type of the content, with charset etc. */
  val contentType: String,

  /** Date this resource was first created. */
  val creation: Instant,

  /** Upload status. */
  val status: RequestStatus,

  /** The last time the [status] was updated. */
  val lastUpdateTime: Instant
)
