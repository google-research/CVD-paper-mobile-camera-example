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

package com.google.android.sensing.db.impl.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.google.android.sensing.db.impl.entities.UploadRequestEntity
import com.google.android.sensing.model.RequestStatus
import com.google.android.sensing.model.UploadRequest
import java.util.Date
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform

@Dao
internal abstract class UploadRequestDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun insertUploadRequestEntity(uploadRequestEntity: UploadRequestEntity)

  @Transaction
  open suspend fun insertUploadRequest(uploadRequest: UploadRequest): String {
    // convert to CaptureInfoEntity and insert
    insertUploadRequestEntity(uploadRequest.toUploadRequestEntity())
    return uploadRequest.requestUuid.toString()
  }

  @Query("""
      SELECT *
      FROM UploadRequestEntity
      WHERE status = :status
    """)
  abstract suspend fun listUploadRequestEntities(status: RequestStatus): List<UploadRequestEntity>

  @Transaction
  open suspend fun listUploadRequests(status: RequestStatus): List<UploadRequest> {
    return listUploadRequestEntities(status).map { it.toUploadRequest() }
  }

  @Query("SELECT * FROM UploadRequestEntity WHERE status=:status")
  abstract fun getUploadRequestEntity(status: RequestStatus): Flow<List<UploadRequestEntity>>

  fun getUploadRequest(status: RequestStatus): Flow<List<UploadRequest>> {
    return getUploadRequestEntity(status).transform {
      it.map { uploadRequestEntity -> uploadRequestEntity.toUploadRequest() }
    }
  }
}

internal fun UploadRequestEntity.toUploadRequest() =
  UploadRequest(
    requestUuid = requestUuid,
    resourceInfoId = resourceInfoId,
    zipFile = zipFile,
    fileSize = fileSize,
    fileOffset = fileOffset,
    bucketName = bucketName,
    uploadRelativeURL = uploadRelativeURL,
    isMultiPart = isMultiPart,
    nextPart = nextPart,
    uploadId = uploadId,
    status = status,
    lastUpdatedTime = Date.from(lastUpdatedTime)
  )

internal fun UploadRequest.toUploadRequestEntity() =
  UploadRequestEntity(
    id = 0,
    requestUuid = requestUuid,
    resourceInfoId = resourceInfoId,
    zipFile = zipFile,
    fileSize = fileSize,
    fileOffset = fileOffset,
    bucketName = bucketName,
    uploadRelativeURL = uploadRelativeURL,
    isMultiPart = isMultiPart,
    nextPart = nextPart,
    uploadId = uploadId,
    status = status,
    lastUpdatedTime = lastUpdatedTime.toInstant()
  )
