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
import com.google.android.sensing.model.ResourceMetaInfo
import com.google.android.sensing.model.UploadRequest
import com.google.android.sensing.model.UploadStatus
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
  private val resourceMetaInfoDao = db.resourceMetaInfoDao()
  private val uploadRequestDao = db.uploadRequestDao()

  override suspend fun addCaptureInfo(captureInfo: CaptureInfo): String {
    return captureInfoDao.insertCaptureInfo(captureInfo)
  }

  override suspend fun addResourceMetaInfo(resourceMetaInfo: ResourceMetaInfo): String {
    return resourceMetaInfoDao.insertResourceMetaInfo(resourceMetaInfo)
  }

  override suspend fun addUploadRequest(uploadRequest: UploadRequest): String {
    return uploadRequestDao.insertUploadRequest(uploadRequest)
  }

  override suspend fun listResourceMetaInfoForParticipant(
    participantId: String
  ): List<ResourceMetaInfo> {
    return resourceMetaInfoDao.listResourceMetaInfoForParticipant(participantId)
  }

  override suspend fun listResourceMetaInfoInCapture(captureId: String): List<ResourceMetaInfo> {
    return resourceMetaInfoDao.listResourceMetaInfoInCapture(captureId)
  }

  override suspend fun listUploadRequests(status: UploadStatus): List<UploadRequest> {
    return uploadRequestDao.listUploadRequests(status)
  }

  override suspend fun updateUploadRequest(uploadRequest: UploadRequest) {
    uploadRequestDao.updateUploadRequest(uploadRequest)
  }

  override suspend fun updateResourceMetaInfo(resourceMetaInfo: ResourceMetaInfo) {
    resourceMetaInfoDao.updateResourceMetaInfo(resourceMetaInfo)
  }

  override suspend fun getResourceMetaInfo(resourceMetaInfoId: String): ResourceMetaInfo {
    return resourceMetaInfoDao.getResourceMetaInfo(resourceMetaInfoId)
      ?: throw ResourceNotFoundException("ResourceMetaInfo", resourceMetaInfoId)
  }

  override suspend fun getCaptureInfo(captureId: String): CaptureInfo {
    return captureInfoDao.getCaptureInfo(captureId)
      ?: throw ResourceNotFoundException("CaptureInfo", captureId)
  }

  override suspend fun deleteCaptureInfo(captureId: String): Boolean {
    return captureInfoDao.deleteCaptureInfo(captureId) == 1
  }

  override suspend fun deleteResourceMetaInfo(resourceMetaInfoId: String): Boolean {
    return resourceMetaInfoDao.deleteResourceMetaInfo(resourceMetaInfoId) == 1
  }

  override suspend fun deleteUploadRequest(resourceMetaInfoId: String): Boolean {
    return uploadRequestDao.deleteUploadRequest(resourceMetaInfoId) == 1
  }

  override suspend fun deleteRecordsInCapture(captureId: String): Boolean {
    var deleted = true
    listResourceMetaInfoInCapture(captureId).forEach {
      deleted = deleted and deleteUploadRequest(it.resourceMetaInfoId)
      deleted = deleted and deleteResourceMetaInfo(it.resourceMetaInfoId)
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
