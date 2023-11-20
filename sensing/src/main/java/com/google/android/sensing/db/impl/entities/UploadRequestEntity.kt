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
import com.google.android.sensing.model.UploadStatus
import java.time.Instant
import java.util.UUID

@Entity(
  indices = [Index(value = ["status"])],
  foreignKeys =
    [
      ForeignKey(
        entity = ResourceMetaInfoEntity::class,
        parentColumns = ["resourceMetaInfoId"],
        childColumns = ["resourceMetaInfoId"],
        onDelete = ForeignKey.CASCADE
      )
    ]
)
/**
 * Database Entity for record-keeping uploads. It has a reference to [resourceMetaInfoId] which a
 * record of this is responsible to upload. Relevantly it requires other file information.
 */
internal data class UploadRequestEntity(
  /** UUID for this record. */
  @PrimaryKey val requestUuid: UUID,

  /** Unique key in [ResourceMetaInfoEntity]: Required to update upload status of the resource. */
  val resourceMetaInfoId: String,

  /** Absolute location of the zip file to be uploaded. Uploader can upload only zip files. */
  val zipFile: String,

  /** Total file size of the zip file to be uploaded in bytes. */
  val fileSize: Long,

  /** Bytes uploaded out of the [fileSize]. */
  val fileOffset: Long,

  /** Bucket name in the blob store for this upload request. */
  val bucketName: String,

  /** Relative URL int he bucket where the data should reside. */
  val uploadRelativeURL: String,

  /** Next file part number to be uploaded. Updated post successful previous part uploading. */
  val nextPart: Int,

  /** If the upload should be in multiple parts. This could be decided based on [fileSize]. */
  val isMultiPart: Boolean,

  /** Assuming this value is either null or unique. Initialized from first upload response. */
  val uploadId: String? = null,

  /** Upload status. */
  val status: UploadStatus,

  /** Time of initialization or successful part upload or completion. */
  val lastUpdatedTime: Instant,
)
