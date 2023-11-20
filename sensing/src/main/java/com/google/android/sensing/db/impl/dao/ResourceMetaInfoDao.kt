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
import androidx.room.Update
import com.google.android.sensing.db.impl.entities.ResourceMetaInfoEntity
import com.google.android.sensing.model.ResourceMetaInfo

@Dao
internal abstract class ResourceMetaInfoDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun insertResourceMetaInfoEntity(resourceMetaInfoEntity: ResourceMetaInfoEntity)

  @Transaction
  open suspend fun insertResourceMetaInfo(resourceMetaInfo: ResourceMetaInfo): String {
    insertResourceMetaInfoEntity(resourceMetaInfo.toResourceMetaInfoEntity())
    return resourceMetaInfo.resourceMetaInfoId
  }

  @Query(
    """
      SELECT *
      FROM ResourceMetaInfoEntity
      WHERE participantId = :participantId
    """
  )
  abstract suspend fun listResourceMetaInfoEntitiesForParticipant(
    participantId: String
  ): List<ResourceMetaInfoEntity>

  @Transaction
  open suspend fun listResourceMetaInfoForParticipant(
    participantId: String
  ): List<ResourceMetaInfo> {
    return listResourceMetaInfoEntitiesForParticipant(participantId).map { it.toResourceMetaInfo() }
  }

  @Query(
    """
      SELECT *
      FROM ResourceMetaInfoEntity
      WHERE captureId = :captureId
    """
  )
  abstract suspend fun listResourceMetaInfoEntitiesInCapture(
    captureId: String
  ): List<ResourceMetaInfoEntity>

  @Transaction
  open suspend fun listResourceMetaInfoInCapture(captureId: String): List<ResourceMetaInfo> {
    return listResourceMetaInfoEntitiesInCapture(captureId).map { it.toResourceMetaInfo() }
  }

  @Update
  abstract suspend fun updateResourceMetaInfoEntity(resourceMetaInfoEntity: ResourceMetaInfoEntity)

  @Transaction
  open suspend fun updateResourceMetaInfo(resourceMetaInfo: ResourceMetaInfo) {
    updateResourceMetaInfoEntity(resourceMetaInfo.toResourceMetaInfoEntity())
  }

  @Query(
    """
      SELECT *
      FROM ResourceMetaInfoEntity
      WHERE resourceMetaInfoId =:resourceMetaInfoId
    """
  )
  abstract suspend fun getResourceMetaInfoEntity(
    resourceMetaInfoId: String
  ): ResourceMetaInfoEntity?

  @Transaction
  open suspend fun getResourceMetaInfo(resourceMetaInfoId: String): ResourceMetaInfo? {
    return getResourceMetaInfoEntity(resourceMetaInfoId)?.toResourceMetaInfo()
  }

  @Query(
    """
    DELETE FROM ResourceMetaInfoEntity WHERE resourceMetaInfoId=:resourceMetaInfoId
  """
  )
  abstract suspend fun deleteResourceMetaInfo(resourceMetaInfoId: String): Int
}

internal fun ResourceMetaInfoEntity.toResourceMetaInfo() =
  ResourceMetaInfo(
    resourceMetaInfoId = resourceMetaInfoId,
    captureId = captureId,
    participantId = participantId,
    captureTitle = captureTitle,
    fileType = fileType,
    resourceFolderRelativePath = resourceFolderRelativePath,
    uploadURL = uploadURL,
    uploadStatus = uploadStatus
  )

internal fun ResourceMetaInfo.toResourceMetaInfoEntity() =
  ResourceMetaInfoEntity(
    id = 0,
    resourceMetaInfoId = resourceMetaInfoId,
    captureId = captureId,
    participantId = participantId,
    captureTitle = captureTitle,
    fileType = fileType,
    resourceFolderRelativePath = resourceFolderRelativePath,
    uploadURL = uploadURL,
    uploadStatus = uploadStatus
  )
