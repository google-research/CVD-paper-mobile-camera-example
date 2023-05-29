package com.google.android.sensory.sensing_sdk.db.impl.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.android.sensory.sensing_sdk.model.CaptureType

@Entity(
  indices = [
    Index(value = ["captureId"], unique = true),
    Index(value = ["captureFolder"], unique = true),
  ]
)
internal data class CaptureInfoEntity(
  @PrimaryKey(autoGenerate = true) val id: Long,
  val participantId: String,
  val captureType: CaptureType,
  val captureFolder: String,
  val captureId: String,
)