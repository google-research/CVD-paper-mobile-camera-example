package com.google.android.sensory.sensing_sdk.db.impl.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.android.sensory.sensing_sdk.model.CaptureType
import java.util.UUID

@Entity(
  indices = [
    Index(value = ["captureId"], unique = true)
    ]
)

internal data class CaptureInfoEntity(
  @PrimaryKey (autoGenerate = true) val id: Long,
  val captureId: String,
  val captureType: CaptureType
)