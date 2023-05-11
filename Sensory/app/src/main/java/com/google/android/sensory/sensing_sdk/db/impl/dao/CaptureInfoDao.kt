package com.google.android.sensory.sensing_sdk.db.impl.dao

import androidx.annotation.VisibleForTesting
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Transaction
import com.google.android.sensory.sensing_sdk.db.impl.entities.CaptureInfoEntity
import com.google.android.sensory.sensing_sdk.model.CaptureInfo

@Dao
internal abstract class CaptureInfoDao {
  @Insert(onConflict = OnConflictStrategy.ABORT)
  abstract suspend fun insertCaptureInfoEntity(captureInfoEntity: CaptureInfoEntity)

  @Transaction
  suspend fun insertCaptureInfo(captureInfo: CaptureInfo): String{
    // convert to CaptureInfoEntity and insert
    val entity: CaptureInfoEntity
    with(captureInfo){
      entity = CaptureInfoEntity(
        id = 0,
        captureId = captureId,
        captureType = captureType
      )
    }
    insertCaptureInfoEntity(entity)
    return captureInfo.captureId
  }
}