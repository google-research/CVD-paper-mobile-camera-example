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

package com.google.android.sensing.db.impl

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.google.android.sensing.DatabaseConfiguration
import com.google.android.sensing.db.Database
import com.google.android.sensing.db.ResourceNotFoundException
import com.google.android.sensing.model.CaptureInfo
import com.google.android.sensing.model.RequestStatus
import com.google.android.sensing.model.ResourceInfo
import com.google.android.sensing.model.UploadRequest
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

/** Implementation of [Database]. */
internal class DatabaseImpl(context: Context, databaseConfig: DatabaseConfiguration) : Database {
  val db: ResourceDatabase =
    if (databaseConfig.inMemory) {
        Room.inMemoryDatabaseBuilder(context, ResourceDatabase::class.java)
      } else {
        Room.databaseBuilder(context, ResourceDatabase::class.java, ENCRYPTED_DATABASE_NAME)
      }
      .apply {
        if (databaseConfig.enableEncryption) {
          openHelperFactory(SupportFactory(SQLiteDatabase.getBytes("PassPhrase".toCharArray())))
        }
      }
      .build()

  private val captureInfoDao = db.captureInfoDao()
  private val resourceInfoDao = db.resourceInfoDao()
  private val uploadRequestDao = db.uploadRequestDao()

  override suspend fun addCaptureInfo(captureInfo: CaptureInfo): String {
    return captureInfoDao.insertCaptureInfo(captureInfo)
  }

  override suspend fun addResourceInfo(resourceInfo: ResourceInfo): String {
    return resourceInfoDao.insertResourceInfo(resourceInfo)
  }

  override suspend fun addUploadRequest(uploadRequest: UploadRequest): String {
    return uploadRequestDao.insertUploadRequest(uploadRequest)
  }

  override suspend fun listResourceInfoForExternalIdentifier(
    externalIdentifier: String
  ): List<ResourceInfo> {
    return resourceInfoDao.listResourceInfoForExternalIdentifier(externalIdentifier)
  }

  override suspend fun listUploadRequests(status: RequestStatus): List<UploadRequest> {
    return uploadRequestDao.listUploadRequests(status)
  }

  override suspend fun updateUploadRequest(uploadRequest: UploadRequest) {
    uploadRequestDao.updateUploadRequest(uploadRequest)
  }

  override suspend fun updateResourceInfo(resourceInfo: ResourceInfo) {
    resourceInfoDao.updateResourceInfo(resourceInfo)
  }

  override suspend fun getResourceInfo(resourceInfoId: String): ResourceInfo {
    return resourceInfoDao.getResourceInfo(resourceInfoId)
  }

  override suspend fun getCaptureInfo(captureId: String): CaptureInfo {
    return db.withTransaction {
      val captureInfo =
        captureInfoDao.getCaptureInfo(captureId)
          ?: throw ResourceNotFoundException("CaptureInfo", captureId)
      val resourceInfoList = resourceInfoDao.listResourceInfoInCapture(captureId)
      captureInfo.copy(resourceInfoList = resourceInfoList)
    }
  }

  override suspend fun deleteRecordsInCapture(captureId: String): Boolean {
    /* We only need to delete CaptureInfo record as we CASCADE it. */
    return captureInfoDao.deleteCaptureInfo(captureId) == 1
  }

  override fun close() {
    db.close()
  }

  companion object {
    const val ENCRYPTED_DATABASE_NAME = "sensor_resources_encrypted.db"
  }
}
