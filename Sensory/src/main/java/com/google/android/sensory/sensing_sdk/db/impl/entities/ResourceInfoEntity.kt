package com.google.android.sensory.sensing_sdk.db.impl.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.android.sensory.sensing_sdk.model.CaptureType
import com.google.android.sensory.sensing_sdk.model.RequestStatus

@Entity(
  indices = [
    // Index(value = ["resourceFolderPath"], unique = true),
    Index(value = ["resourceInfoId"], unique = true),
    Index(value = ["captureId"]),
    Index(value = ["participantId"])
  ]
)
/** Information about the resource collected per capture. This is not involved in uploading.*/
internal data class ResourceInfoEntity (
  @PrimaryKey(autoGenerate = true) val id: Long,
  val resourceInfoId: String,
  val captureId: String,
  val participantId: String,
  val captureType: CaptureType,
  val title: String,
  val fileType: String,
  val resourceFolderPath: String,
  /** uploadUrl should be known at instance creation and not changed post partial uploading.*/
  val uploadURL: String,
  val status: RequestStatus
)