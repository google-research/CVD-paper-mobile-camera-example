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
import androidx.room.Update
import com.google.android.sensing.db.impl.entities.ResourceInfoEntity
import com.google.android.sensing.model.ResourceInfo

@Dao
internal abstract class ResourceInfoDao {
  @Insert(onConflict = OnConflictStrategy.ABORT)
  abstract suspend fun insertResourceInfoEntity(resourceInfoEntity: ResourceInfoEntity)

  @Transaction
  open suspend fun insertResourceInfo(resourceInfo: ResourceInfo): String {
    insertResourceInfoEntity(resourceInfo.toResourceInfoEntity())
    return resourceInfo.resourceInfoId
  }

  @Query(
    """
      SELECT *
      FROM ResourceInfoEntity
      WHERE externalIdentifier = :externalIdentifier
    """
  )
  abstract suspend fun listResourceInfoEntitiesForExternalIdentifier(
    externalIdentifier: String
  ): List<ResourceInfoEntity>

  @Transaction
  open suspend fun listResourceInfoForExternalIdentifier(
    externalIdentifier: String
  ): List<ResourceInfo> {
    return listResourceInfoEntitiesForExternalIdentifier(externalIdentifier).map {
      it.toResourceInfo()
    }
  }

  @Query(
    """
      SELECT *
      FROM ResourceInfoEntity
      WHERE captureId = :captureId
    """
  )
  abstract suspend fun listResourceInfoEntitiesInCapture(
    captureId: String
  ): List<ResourceInfoEntity>

  @Transaction
  open suspend fun listResourceInfoInCapture(captureId: String): List<ResourceInfo> {
    return listResourceInfoEntitiesInCapture(captureId).map { it.toResourceInfo() }
  }

  @Update abstract suspend fun updateResourceInfoEntity(resourceInfoEntity: ResourceInfoEntity)

  @Transaction
  open suspend fun updateResourceInfo(resourceInfo: ResourceInfo) {
    updateResourceInfoEntity(resourceInfo.toResourceInfoEntity())
  }

  @Query(
    """
      SELECT *
      FROM ResourceInfoEntity
      WHERE resourceInfoId =:resourceInfoId
    """
  )
  abstract suspend fun getResourceInfoEntity(resourceInfoId: String): ResourceInfoEntity?

  @Transaction
  open suspend fun getResourceInfo(resourceInfoId: String): ResourceInfo {
    return getResourceInfoEntity(resourceInfoId)?.toResourceInfo()
      ?: throw Exception("ResourceInfo with resourceInfoId = $resourceInfoId not found!")
  }
}

internal fun ResourceInfoEntity.toResourceInfo() =
  ResourceInfo(
    resourceInfoId = resourceInfoId,
    captureId = captureId,
    externalIdentifier = externalIdentifier,
    localLocation = localLocation,
    remoteLocation = remoteLocation,
    resourceTitle = resourceTitle,
    contentType = contentType,
    status = status,
  )

internal fun ResourceInfo.toResourceInfoEntity() =
  ResourceInfoEntity(
    resourceInfoId = resourceInfoId,
    captureId = captureId,
    externalIdentifier = externalIdentifier,
    localLocation = localLocation,
    remoteLocation = remoteLocation,
    resourceTitle = resourceTitle,
    contentType = contentType,
    status = status,
  )
