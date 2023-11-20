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

package com.google.android.sensing.db.impl

import android.content.Context
import androidx.room.Room
import com.google.android.sensing.DatabaseErrorStrategy
import com.google.android.sensing.db.Database
import com.google.android.sensing.db.ResourceNotFoundException
import com.google.android.sensing.model.CaptureInfo
import com.google.android.sensing.model.RequestStatus
import com.google.android.sensing.model.ResourceInfo
import com.google.android.sensing.model.UploadRequest
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

/** Implementation of [Database]. */
internal class DatabaseImpl(context: Context, databaseConfig: DatabaseConfig) : Database {
  val db: ResourceDatabase =
    Room.databaseBuilder(context, ResourceDatabase::class.java, ENCRYPTED_DATABASE_NAME)
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

  override suspend fun listResourceInfoForParticipant(participantId: String): List<ResourceInfo> {
    return resourceInfoDao.listResourceInfoForParticipant(participantId)
  }

  override suspend fun listResourceInfoInCapture(captureId: String): List<ResourceInfo> {
    return resourceInfoDao.listResourceInfoInCapture(captureId)
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
    return captureInfoDao.getCaptureInfo(captureId)
      ?: throw ResourceNotFoundException("CaptureInfo", captureId)
  }

  override suspend fun deleteCaptureInfo(captureId: String): Boolean {
    return captureInfoDao.deleteCaptureInfo(captureId) == 1
  }

  override suspend fun deleteResourceInfo(resourceInfoId: String): Boolean {
    return resourceInfoDao.deleteResourceInfo(resourceInfoId) == 1
  }

  override suspend fun deleteUploadRequest(resourceInfoId: String): Boolean {
    return uploadRequestDao.deleteUploadRequest(resourceInfoId) == 1
  }

  override suspend fun deleteRecordsInCapture(captureId: String): Boolean {
    var deleted = true
    listResourceInfoInCapture(captureId).forEach {
      deleted = deleted and deleteUploadRequest(it.resourceInfoId)
      deleted = deleted and deleteResourceInfo(it.resourceInfoId)
    }
    deleted = deleted and deleteCaptureInfo(captureId)
    return deleted
  }

  companion object {
    const val ENCRYPTED_DATABASE_NAME = "sensor_resources_encrypted.db"
  }
}

data class DatabaseConfig(
  val enableEncryption: Boolean,
  val databaseErrorStrategy: DatabaseErrorStrategy
)
