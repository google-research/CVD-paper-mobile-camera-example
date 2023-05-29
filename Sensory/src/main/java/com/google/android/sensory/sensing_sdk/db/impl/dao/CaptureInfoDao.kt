/*
 * Copyright 2022 Google LLC
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

package com.google.android.sensory.sensing_sdk.db.impl.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Transaction
import com.google.android.sensory.sensing_sdk.db.impl.entities.CaptureInfoEntity
import com.google.android.sensory.sensing_sdk.model.CaptureInfo

@Dao
internal abstract class CaptureInfoDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun insertCaptureInfoEntity(captureInfoEntity: CaptureInfoEntity)

  @Transaction
  open suspend fun insertCaptureInfo(captureInfo: CaptureInfo): String {
    // convert to CaptureInfoEntity and insert
    val entity: CaptureInfoEntity
    with(captureInfo) {
      entity =
        CaptureInfoEntity(
          id = 0,
          participantId = participantId,
          captureType = captureType,
          captureFolder = captureFolder,
          captureId = captureId
        )
    }
    insertCaptureInfoEntity(entity)
    return captureInfo.participantId
  }
}
