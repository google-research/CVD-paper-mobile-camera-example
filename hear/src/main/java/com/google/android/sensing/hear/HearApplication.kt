/*
 * Copyright 2024 Google LLC
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

package com.google.android.sensing.hear

import android.app.Application
import android.content.Context
import com.google.android.sensing.DatabaseConfiguration
import com.google.android.sensing.MinioInternalIDPAuthenticator
import com.google.android.sensing.SensingEngineConfiguration
import com.google.android.sensing.SensorManager
import com.google.android.sensing.ServerConfiguration
import java.io.IOException
import java.util.Properties
import timber.log.Timber

class HearApplication : Application(), SensingEngineConfiguration.Provider {

  private lateinit var sensorManager: SensorManager

  override fun onCreate() {
    super.onCreate()
    if (BuildConfig.DEBUG) {
      Timber.plant(Timber.DebugTree())
    }
    // workaround for making them available as soon as applicationContext is available.
    sensorManager = constructSensorManager()
  }

  private fun constructSensorManager() = SensorManager.getInstance(applicationContext)

  override fun getSensingEngineConfiguration(): SensingEngineConfiguration {
    return SensingEngineConfiguration(
      databaseConfiguration = DatabaseConfiguration(enableEncryption = false),
      serverConfiguration =
        getLocalProperties()?.let {
          ServerConfiguration(
            baseUrl = it.getProperty("BLOBSTORE_BASE_URL"),
            baseAccessUrl = it.getProperty("BLOBSTORE_BASE_ACCESS_URL"),
            bucketName = it.getProperty("BLOBSTORE_BUCKET_NAME"),
            authenticator =
              /* Implement MinioInternalIDPAuthenticator directly when built-in IDP is required. */
              object : MinioInternalIDPAuthenticator() {
                override fun getUserName() = it.getProperty("BLOBSTORE_USER")
                override fun getPassword() = it.getProperty("BLOBSTORE_PASSWORD")
              }
            /* Implement Authenticator interface directly when external IDP is OpenIDP or LDAP. */
            /* Implement MinioIDPPluginAuthenticator when external IDP is a custom one. */
            )
        }
    )
  }

  private fun getLocalProperties(): Properties? {
    return try {
      Properties().apply { load(applicationContext.assets.open("local.properties")) }
    } catch (e: IOException) {
      Timber.d("No local.properties file. Moving with application defined configuration!")
      null
    }
  }

  companion object {
    fun getSensorManager(context: Context) =
      (context.applicationContext as HearApplication).sensorManager
  }
}
