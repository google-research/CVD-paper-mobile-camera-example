package com.google.android.sensory.sensing_sdk.db.impl.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.android.sensory.sensing_sdk.model.RequestStatus
import java.time.Instant
import java.util.UUID

@Entity(
  indices = [
    Index(value = ["status"]),
    Index(value = ["requestUuid"], unique = true)
  ]
)
internal data class UploadRequestEntity (
  @PrimaryKey(autoGenerate = true) val id: Long,
  val requestUuid: UUID,
  val resourceInfoId: String,
  val zipFile: String,
  val fileSize: Long,
  val uploadURL: String,
  val lastUpdatedTime: Instant,
  val bytesUploaded: Long,
  val status: RequestStatus,
  val nextPart: Int,
  /** Assuming this value is either null or unique. Null because this is updated from first upload response.*/
  val uploadId: String? = null
)