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

package com.google.android.sensing.db.impl.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.google.android.sensing.capture.CaptureRequest
import com.google.android.sensing.db.impl.entities.CaptureInfoEntity
import com.google.android.sensing.model.CaptureInfo
import com.google.gson.Gson
import java.time.Instant
import java.util.Date

@Dao
internal abstract class CaptureInfoDao {
  @Insert(onConflict = OnConflictStrategy.ABORT)
  abstract suspend fun insertCaptureInfoEntity(captureInfoEntity: CaptureInfoEntity)

  @Transaction
  open suspend fun insertCaptureInfo(captureInfo: CaptureInfo): String {
    insertCaptureInfoEntity(captureInfo.toCaptureInfoEntity())
    return captureInfo.externalIdentifier
  }

  @Query("""
    SELECT * FROM CaptureInfoEntity WHERE captureId=:captureId
  """)
  abstract suspend fun getCaptureInfoEntity(captureId: String): List<CaptureInfoEntity>

  @Transaction
  open suspend fun getCaptureInfo(captureId: String): CaptureInfo? {
    return getCaptureInfoEntity(captureId).firstOrNull()?.toCaptureInfo()
  }

  @Query("""
    DELETE FROM CaptureInfoEntity WHERE captureId=:captureId
  """)
  abstract suspend fun deleteCaptureInfo(captureId: String): Int
}

internal fun CaptureInfo.toCaptureInfoEntity() =
  CaptureInfoEntity(
    captureId = captureId,
    externalIdentifier = externalIdentifier,
    captureRequestType = captureRequest::class.java.name,
    captureRequest = Gson().toJson(captureRequest),
    captureFolder = captureFolder,
    captureTime = captureTime?.toInstant() ?: Instant.now(),
  )

internal fun CaptureInfoEntity.toCaptureInfo() =
  CaptureInfo(
    captureId = captureId,
    externalIdentifier = externalIdentifier,
    captureRequest =
      Gson().fromJson(captureRequest, Class.forName(captureRequestType)) as CaptureRequest,
    captureFolder = captureFolder,
    captureTime = Date.from(captureTime)
  )
