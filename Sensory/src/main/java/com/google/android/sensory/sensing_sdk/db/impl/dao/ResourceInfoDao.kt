package com.google.android.sensory.sensing_sdk.db.impl.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.google.android.sensory.sensing_sdk.db.impl.entities.ResourceInfoEntity
import com.google.android.sensory.sensing_sdk.model.ResourceInfo

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
      WHERE participantId = :participantId
    """
  )
  abstract suspend fun listResourceInfoEntitiesForParticipant(participantId: String): List<ResourceInfoEntity>

  @Transaction
  open suspend fun listResourceInfoForParticipant(participantId: String): List<ResourceInfo> {
    return listResourceInfoEntitiesForParticipant(participantId).map {
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
  abstract suspend fun listResourceInfoEntitiesInCapture(captureId: String): List<ResourceInfoEntity>

  @Transaction
  open suspend fun listResourceInfoInCapture(captureId: String): List<ResourceInfo> {
    return listResourceInfoEntitiesInCapture(captureId).map {
      it.toResourceInfo()
    }
  }

  @Update
  abstract suspend fun updateResourceInfoEntity(resourceInfoEntity: ResourceInfoEntity)

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
    participantId = participantId,
    captureType = captureType,
    title = title,
    fileType = fileType,
    resourceFolderPath = resourceFolderPath,
    uploadURL = uploadURL,
    status = status
  )

internal fun ResourceInfo.toResourceInfoEntity() =
  ResourceInfoEntity(
    id = 0,
    resourceInfoId = resourceInfoId,
    captureId = captureId,
    participantId = participantId,
    captureType = captureType,
    title = title,
    fileType = fileType,
    resourceFolderPath = resourceFolderPath,
    uploadURL = uploadURL,
    status = status
  )