package com.google.android.sensory.sensing_sdk.db.impl.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.google.android.sensory.sensing_sdk.db.impl.entities.ResourceInfoEntity
import com.google.android.sensory.sensing_sdk.db.impl.entities.UploadRequestEntity
import com.google.android.sensory.sensing_sdk.model.RequestStatus
import com.google.android.sensory.sensing_sdk.model.ResourceInfo
import com.google.android.sensory.sensing_sdk.model.UploadRequest
import java.util.Date

@Dao
internal abstract class UploadRequestDao {
  @Insert(onConflict = OnConflictStrategy.ABORT)
  abstract suspend fun insertUploadRequestEntity(uploadRequestEntity: UploadRequestEntity)

  @Transaction
  suspend fun insertUploadRequest(uploadRequest: UploadRequest): String{
    // convert to CaptureInfoEntity and insert
    insertUploadRequestEntity(uploadRequest.toUploadRequestEntity())
    return uploadRequest.requestId
  }

  @Query(
    """
      SELECT *
      FROM UploadRequestEntity
      WHERE status = :status
    """
  )
  abstract suspend fun listUploadRequestEntities(status: RequestStatus): List<UploadRequestEntity>

  @Transaction
  suspend fun listUploadRequests(status: RequestStatus): List<UploadRequest> {
    return listUploadRequestEntities(status).map{
      it.toUploadRequest()
    }
  }

  @Update
  abstract suspend fun updateUploadRequestEntity(uploadRequestEntity: UploadRequestEntity)

  @Transaction
  suspend fun updateUploadRequest(uploadRequest: UploadRequest){
    updateUploadRequestEntity(uploadRequest.toUploadRequestEntity())
  }
}

internal fun UploadRequestEntity.toUploadRequest() =
  UploadRequest(
    requestId = requestId,
    resourceInfoId = resourceInfoId,
    lastUpdatedTime = Date.from(lastUpdatedTime),
    bytesUploaded = bytesUploaded,
    status = status,
    uploadId = uploadId
  )

internal fun UploadRequest.toUploadRequestEntity() =
  UploadRequestEntity(
    id = 0,
    requestId = requestId,
    resourceInfoId = resourceInfoId,
    lastUpdatedTime = lastUpdatedTime.toInstant(),
    bytesUploaded = bytesUploaded,
    status = status,
    uploadId = uploadId
  )