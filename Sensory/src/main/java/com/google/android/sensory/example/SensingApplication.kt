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

package com.google.android.sensory.example

import android.app.Application
import android.content.Context
import com.google.android.fhir.DatabaseErrorStrategy
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.FhirEngineConfiguration
import com.google.android.fhir.FhirEngineProvider
import com.google.android.fhir.datacapture.DataCaptureConfig
import com.google.android.fhir.datacapture.QuestionnaireFragment
import com.google.android.sensory.example.fhir_data.PPGSensorCaptureViewHolderFactory
import com.google.android.sensory.example.fhir_data.PhotoCaptureViewHolderFactory
import com.google.android.sensory.sensing_sdk.Authenticator
import com.google.android.sensory.sensing_sdk.SensingEngine
import com.google.android.sensory.sensing_sdk.SensingEngineConfiguration
import com.google.android.sensory.sensing_sdk.SensingEngineProvider
import com.google.android.sensory.sensing_sdk.ServerConfiguration
import java.util.Properties

class SensingApplication : Application(), DataCaptureConfig.Provider {
  private val fhirEngine by lazy { constructFhirEngine() }
  private val sensingEngine by lazy { constructSensingEngine() }

  override fun onCreate() {
    super.onCreate()
    initSensingAndFhirEngines()
  }

  private fun initSensingAndFhirEngines() {
    val properties = Properties().apply { load(applicationContext.assets.open("local.properties")) }
    val sensingEngineConfiguration =
      SensingEngineConfiguration(
        enableEncryptionIfSupported = false,
        serverConfiguration =
          ServerConfiguration(
            baseUrl = properties.getProperty("BLOBSTORE_BASE_URL"),
            baseAccessUrl = properties.getProperty("BLOBSTORE_BASE_ACCESS_URL"),
            bucketName = properties.getProperty("BLOBSTORE_BUCKET_NAME"),
            authenticator =
              object : Authenticator {
                override fun getUserName() = properties.getProperty("BLOBSTORE_USER")
                override fun getPassword() = properties.getProperty("BLOBSTORE_PASSWORD")
              }
          )
      )
    /**
     * Local hapi fhir server was used to test the following. Add
     * [FhirEngineConfiguration.serverConfiguration] for your own fhir server.
     */
    SensingEngineProvider.init(sensingEngineConfiguration)
    FhirEngineProvider.init(
      FhirEngineConfiguration(
        enableEncryptionIfSupported = true,
        DatabaseErrorStrategy.RECREATE_AT_OPEN,
        com.google.android.fhir.ServerConfiguration(properties.getProperty("FHIR_BASE_URL"))
      )
    )
  }

  private fun constructFhirEngine(): FhirEngine {
    return FhirEngineProvider.getInstance(this)
  }

  private fun constructSensingEngine(): SensingEngine {
    return SensingEngineProvider.getOrCreateSensingEngine(applicationContext)
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
}
