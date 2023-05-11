package com.google.android.sensory.sensing_sdk.db.impl.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.android.sensory.sensing_sdk.model.RequestStatus
import java.time.Instant

@Entity(
  indices = [
    Index(value = ["status"]),
    Index(value = ["resourceInfoId"], unique = true),
    Index(value = ["requestId"], unique = true)
  ],
  foreignKeys = [
    ForeignKey(
      entity = ResourceInfoEntity::class,
      parentColumns = ["resourceInfoId"],
      childColumns = ["resourceInfoId"],
    )
  ]
)
internal data class UploadRequestEntity (
  @PrimaryKey(autoGenerate = true) val id: Long,
  val requestId: String,
  val resourceInfoId: String,
  val lastUpdatedTime: Instant,
  val bytesUploaded: Long,
  val status: RequestStatus,
  val uploadId: String? = null
)