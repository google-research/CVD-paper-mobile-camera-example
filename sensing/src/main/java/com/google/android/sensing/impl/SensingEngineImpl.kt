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

package com.google.android.sensing.impl

import android.content.Context
import android.content.Intent
import com.google.android.sensing.SensingEngine
import com.google.android.sensing.ServerConfiguration
import com.google.android.sensing.db.Database
import com.google.android.sensing.db.ResourceNotFoundException
import com.google.android.sensing.model.CaptureInfo
import com.google.android.sensing.model.RequestStatus
import com.google.android.sensing.model.ResourceInfo
import com.google.android.sensing.model.UploadRequest
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * @param database Interface to interact with room database.
 * @param context [Context] to access fragmentManager, to launch fragments, to access files and
 * resources in the application context.
 * @param serverConfiguration
 */
internal class SensingEngineImpl(
  private val database: Database,
  private val context: Context,
  private val serverConfiguration: ServerConfiguration?,
) : SensingEngine {

  override suspend fun captureSensorData(pendingIntent: Intent) {
    TODO("Not yet implemented")
  }

  override suspend fun listResourceInfoForExternalIdentifier(
    externalIdentifier: String
  ): List<ResourceInfo> {
    return database.listResourceInfoForExternalIdentifier(externalIdentifier)
  }

  override suspend fun getResourceInfo(resourceInfoId: String): ResourceInfo? {
    return try {
      database.getResourceInfo(resourceInfoId)
    } catch (e: ResourceNotFoundException) {
      null
    }
  }

  override suspend fun updateResourceInfo(resourceInfo: ResourceInfo) {
    database.updateResourceInfo(resourceInfo)
  }

  override suspend fun updateUploadRequest(uploadRequest: UploadRequest) {
    return database.updateUploadRequest(uploadRequest)
  }

  override suspend fun listUploadRequest(status: RequestStatus): List<UploadRequest> {
    return database.listUploadRequests(status)
  }

  override suspend fun getCaptureInfo(captureId: String): CaptureInfo {
    return database.getCaptureInfo(captureId)
  }

  override suspend fun deleteDataInCapture(captureId: String): Boolean {
    val captureInfo =
      try {
        getCaptureInfo(captureId)
      } catch (e: ResourceNotFoundException) {
        null
      } ?: return true

    // Step 1: Delete db records
    database.deleteRecordsInCapture(captureId)
    // Step 2: delete the captureFolder
    val captureFile = File(context.filesDir, captureInfo.captureFolder)
    val parentFile = captureFile.parentFile
    val deleted: Boolean
    withContext(Dispatchers.IO) {
      deleted = captureFile.deleteRecursively()
      // delete Participant's folder if there are no data
      if (parentFile?.list()?.isEmpty() == true) {
        parentFile.delete()
      }
    }
    return deleted
  }

  override suspend fun deleteSensorData(uploadURL: String) {
    TODO("Not yet implemented")
  }

  override suspend fun deleteSensorMetaData(uploadURL: String) {
    TODO("Not yet implemented")
  }
}
