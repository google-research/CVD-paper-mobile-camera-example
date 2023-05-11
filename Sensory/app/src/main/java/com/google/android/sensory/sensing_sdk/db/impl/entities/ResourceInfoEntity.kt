package com.google.android.sensory.sensing_sdk.db.impl.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.android.sensory.sensing_sdk.model.RequestStatus
import java.util.UUID

@Entity(
  indices = [
    Index(value = ["uploadURL"], unique = true),
    Index(value = ["fileURI"], unique = true),
    Index(value = ["resourceInfoId"], unique = true)
  ],
  foreignKeys = [
    ForeignKey(
      entity = CaptureInfoEntity::class,
      parentColumns = ["captureId"],
      childColumns = ["captureId"],
    )
  ]
)
internal data class ResourceInfoEntity (
  @PrimaryKey(autoGenerate = true) val id: Long,
  val resourceInfoId: String,
  val captureId: String,
  val fileSize: Long,
  val fileType: String,
  val fileURI: String,
  val uploadURL: String,
  val status: RequestStatus
)
