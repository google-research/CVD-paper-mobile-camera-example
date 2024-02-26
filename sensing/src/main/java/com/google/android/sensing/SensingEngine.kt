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

package com.google.android.sensing

import android.content.Context
import android.content.Intent
import com.google.android.sensing.db.Database
import com.google.android.sensing.impl.SensingEngineImpl
import com.google.android.sensing.model.CaptureInfo
import com.google.android.sensing.model.RequestStatus
import com.google.android.sensing.model.ResourceInfo
import com.google.android.sensing.model.UploadRequest

/**
 * The Sensing Engine interface that handles the local storage of captured resources.
 *
 * TODO: CRUD APIs for UploadRequest to use only the upload mechanism this Engine provides.
 *
 * TODO: Order APIs nicely
 */
interface SensingEngine {

  /** Get CaptureInfo record given @param captureId */
  suspend fun getCaptureInfo(captureId: String): CaptureInfo

  /** Delete all data associated with [captureId] */
  suspend fun deleteDataInCapture(captureId: String): Boolean

  /**
   * Lists all ResourceInfo given a externalIdentifier. This will return all ResourceInfo across
   * multiple capturings.
   */
  suspend fun listResourceInfoForExternalIdentifier(externalIdentifier: String): List<ResourceInfo>

  suspend fun getResourceInfo(resourceInfoId: String): ResourceInfo?

  suspend fun updateResourceInfo(resourceInfo: ResourceInfo)

  /** To support 3P apps */
  suspend fun captureSensorData(pendingIntent: Intent)

  /** Update given [uploadRequest] in database. */
  suspend fun updateUploadRequest(uploadRequest: UploadRequest)

  /**
   * Get [UploadRequest] corresponding to the [ResourceInfo] given [ResourceInfo.resourceInfoId].
   * Application developers can use this API to monitor not just upload status but also progress.
   */
  suspend fun listUploadRequest(status: RequestStatus): List<UploadRequest>

  /** Delete data stored in blobstore */
  suspend fun deleteSensorData(uploadURL: String)
  suspend fun deleteSensorMetaData(uploadURL: String)

  companion object {
    @Volatile private var instance: SensingEngine? = null
    fun getInstance(context: Context) =
      instance
        ?: synchronized(this) {
          instance
            ?: run {
                val appContext = context.applicationContext
                val sensingEngineConfiguration =
                  if (appContext is SensingEngineConfiguration.Provider) {
                    appContext.getSensingEngineConfiguration()
                  } else SensingEngineConfiguration()
                with(sensingEngineConfiguration) {
                  val database = Database.getInstance(context, databaseConfiguration)
                  SensingEngineImpl(database, context, serverConfiguration)
                }
              }
              .also { instance = it }
        }
  }
}
