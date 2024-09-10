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

package com.google.android.sensory

import android.app.Application
import android.content.Context
import com.google.android.fhir.DatabaseErrorStrategy
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.FhirEngineConfiguration
import com.google.android.fhir.FhirEngineProvider
import com.google.android.fhir.datacapture.DataCaptureConfig
import com.google.android.fhir.datacapture.QuestionnaireFragment
import com.google.android.sensing.DatabaseConfiguration
import com.google.android.sensing.MinioInternalIDPAuthenticator
import com.google.android.sensing.SensingEngine
import com.google.android.sensing.SensingEngineConfiguration
import com.google.android.sensing.SensingEngineProvider
import com.google.android.sensing.ServerConfiguration
import com.google.android.sensory.fhir_data.PPGSensorCaptureViewHolderFactory
import com.google.android.sensory.fhir_data.PhotoCaptureViewHolderFactory
import java.io.IOException
import java.util.Properties
import timber.log.Timber

class SensingApplication :
  Application(), DataCaptureConfig.Provider, SensingEngineConfiguration.Provider {
  private val fhirEngine by lazy { constructFhirEngine() }
  private val sensingEngine by lazy { constructSensingEngine() }

  override fun onCreate() {
    if (BuildConfig.DEBUG) {
      Timber.plant(Timber.DebugTree())
    }
    super.onCreate()
    initFhirEngine()
  }

  private fun initFhirEngine() {
    /**
     * Local hapi fhir server was used to test the following. Add
     * [FhirEngineConfiguration.serverConfiguration] for your own fhir server.
     */
    FhirEngineProvider.init(
      FhirEngineConfiguration(
        enableEncryptionIfSupported = false,
        databaseErrorStrategy = DatabaseErrorStrategy.RECREATE_AT_OPEN,
        serverConfiguration =
          getLocalProperties()?.let {
            com.google.android.fhir.ServerConfiguration(it.getProperty("FHIR_BASE_URL"))
          }
      )
    )
  }

  private fun constructFhirEngine(): FhirEngine {
    return FhirEngineProvider.getInstance(applicationContext)
  }

  private fun constructSensingEngine(): SensingEngine {
    return SensingEngineProvider.getInstance(applicationContext)
  }

  companion object {
    const val SHARED_PREFS_KEY = "shared_prefs_key"
    const val CURRENT_PATIENT_ID = "patient-id"
    const val CUSTOM_VIEW_HOLDER_FACTORY_TAG = "sensor_capture"
    // The following version constant should be updated whenever we ship a new version of the app.
    // The version format is composed of two parts vXsY, which stands for version X subversion Y.
    // We do not use "." in the version string to avoid confusion with file suffixes.
    const val APP_VERSION = "v2s1"
    fun fhirEngine(context: Context) = (context.applicationContext as SensingApplication).fhirEngine
    fun sensingEngine(context: Context) =
      (context.applicationContext as SensingApplication).sensingEngine
  }

  override fun getDataCaptureConfig(): DataCaptureConfig {
    return DataCaptureConfig(
      questionnaireItemViewHolderFactoryMatchersProviderFactory = { tag ->
        CUSTOM_VIEW_HOLDER_FACTORY_TAG
        object : QuestionnaireFragment.QuestionnaireItemViewHolderFactoryMatchersProvider() {

          override fun get():
            List<QuestionnaireFragment.QuestionnaireItemViewHolderFactoryMatcher> {
            return listOf(
              QuestionnaireFragment.QuestionnaireItemViewHolderFactoryMatcher(
                PhotoCaptureViewHolderFactory
              ) { questionnaireItem ->
                questionnaireItem
                  .getExtensionByUrl(PhotoCaptureViewHolderFactory.WIDGET_EXTENSION)
                  .let {
                    if (it == null) false
                    else it.value.toString() == PhotoCaptureViewHolderFactory.WIDGET_TYPE
                  }
              },
              QuestionnaireFragment.QuestionnaireItemViewHolderFactoryMatcher(
                PPGSensorCaptureViewHolderFactory
              ) { questionnaireItem,
                ->
                questionnaireItem
                  .getExtensionByUrl(PPGSensorCaptureViewHolderFactory.WIDGET_EXTENSION)
                  .let {
                    if (it == null) false
                    else it.value.toString() == PPGSensorCaptureViewHolderFactory.WIDGET_TYPE
                  }
              }
            )
          }
        }
      }
    )
  }

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
}
