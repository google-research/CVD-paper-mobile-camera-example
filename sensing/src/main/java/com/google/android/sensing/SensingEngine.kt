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
import com.google.android.sensing.db.Database
import com.google.android.sensing.impl.SensingEngineImpl
import com.google.android.sensing.model.CaptureInfo
import com.google.android.sensing.model.ResourceInfo

/**
 * Interface defining interactions with a sensing engine, responsible for managing captured data and
 * metadata.
 */
interface SensingEngine {

  /**
   * Retrieves the CaptureInfo record associated with a specific capture session.
   *
   * @param captureId The unique identifier for a capture session.
   * @return The CaptureInfo object containing metadata about the capture session,
   * ```
   *         or null if no record is found.
   * ```
   */
  suspend fun getCaptureInfo(captureId: String): CaptureInfo

  /**
   * Deletes all data associated with a specific capture session. This includes sensor data,
   * metadata (CaptureInfo), and potentially related resources.
   *
   * @param captureId The unique identifier for the capture session to be deleted.
   * @return True if the deletion was successful, false otherwise.
   */
  suspend fun deleteDataInCapture(captureId: String): Boolean

  /**
   * Retrieves a list of ResourceInfo objects associated with a given external identifier. This
   * search spans across multiple capture sessions.
   *
   * @param externalIdentifier An identifier used to link resources, potentially across different
   * capture sessions.
   * @return A list of ResourceInfo objects matching the external identifier.
   * ```
   *         The list may be empty if no resources are found.
   * ```
   */
  suspend fun listResourceInfoForExternalIdentifier(externalIdentifier: String): List<ResourceInfo>

  /**
   * Retrieves a specific ResourceInfo object based on its unique ID.
   *
   * @param resourceInfoId The unique identifier of the ResourceInfo object.
   * @return The ResourceInfo object if found, otherwise null.
   */
  suspend fun getResourceInfo(resourceInfoId: String): ResourceInfo?

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
                  SensingEngineImpl(database, context)
                }
              }
              .also { instance = it }
        }
  }
}
