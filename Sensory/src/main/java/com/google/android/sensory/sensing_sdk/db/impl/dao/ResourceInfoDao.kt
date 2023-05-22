package com.google.android.sensory.sensing_sdk.db.impl.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.google.android.sensory.sensing_sdk.db.impl.entities.ResourceInfoEntity
import com.google.android.sensory.sensing_sdk.model.ResourceInfo
import java.lang.Exception

@Dao
internal abstract class ResourceInfoDao {
  @Insert(onConflict = OnConflictStrategy.ABORT)
  abstract suspend fun insertResourceInfoEntity(resourceInfoEntity: ResourceInfoEntity)

  @Transaction
  open suspend fun insertResourceInfo(resourceInfo: ResourceInfo): String{
    insertResourceInfoEntity(resourceInfo.toResourceInfoEntity())
    return resourceInfo.resourceInfoId
  }

  @Query(
    """
      SELECT *
      FROM ResourceInfoEntity
      WHERE captureId = :captureId
    """
  )
  abstract suspend fun listResourceInfoEntities(captureId: String): List<ResourceInfoEntity>

  @Transaction
  open suspend fun listResourceInfo(captureId: String): List<ResourceInfo> {
    return listResourceInfoEntities(captureId).map {
      it.toResourceInfo()
    }
  }

  @Update
  abstract suspend fun updateResourceInfoEntity(resourceInfoEntity: ResourceInfoEntity)

  @Transaction
  open suspend fun updateResourceInfo(resourceInfo: ResourceInfo){
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
    captureType = captureType,
    fileType = fileType,
    fileURI = fileURI,
    uploadURL = uploadURL,
    status = status
  )

internal fun ResourceInfo.toResourceInfoEntity() =
  ResourceInfoEntity(
    id = 0,
    resourceInfoId = resourceInfoId,
    captureId = captureId,
    captureType = captureType,
    fileType = fileType,
    fileURI = fileURI,
    uploadURL = uploadURL,
    status = status
  )