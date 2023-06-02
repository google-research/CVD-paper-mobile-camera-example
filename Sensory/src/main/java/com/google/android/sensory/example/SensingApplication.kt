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
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.FhirEngineProvider
import com.google.android.fhir.datacapture.DataCaptureConfig
import com.google.android.fhir.datacapture.QuestionnaireFragment
import com.google.android.sensory.example.fhir_data.PPGSensorCaptureViewHolderFactory
import com.google.android.sensory.example.fhir_data.PhotoCaptureViewHolderFactory
import com.google.android.sensory.sensing_sdk.SensingEngine
import com.google.android.sensory.sensing_sdk.SensingEngineProvider
import com.google.android.sensory.sensing_sdk.UploadConfiguration
import java.util.Properties

class SensingApplication : Application(), DataCaptureConfig.Provider {
  private val fhirEngine by lazy { constructFhirEngine() }
  private var dataCaptureConfig: DataCaptureConfig? = null
  private val sensingEngine by lazy { constructSensingEngine() }
  private val uploadConfiguration by lazy { constructUploadConfiguration() }

  override fun onCreate() {
    super.onCreate()
    dataCaptureConfig = DataCaptureConfig()
  }

  private fun constructFhirEngine(): FhirEngine {
    return FhirEngineProvider.getInstance(this)
  }

  private fun constructSensingEngine(): SensingEngine {
    SensingEngineProvider.init(uploadConfiguration)
    return SensingEngineProvider.getOrCreateSensingEngine(applicationContext, false)
  }

  private fun constructUploadConfiguration(): UploadConfiguration {
    val properties = Properties().apply { load(applicationContext.assets.open("local.properties")) }
    return UploadConfiguration(
      HOST = properties.getProperty("HOST"),
      ACCESS_HOST = properties.getProperty("ACCESS_HOST"),
      bucketName = properties.getProperty("BUCKET_NAME"),
      user = properties.getProperty("USER"),
      password = properties.getProperty("PASSWORD")
    )
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

    fun uploadConfiguration(context: Context) =
      (context.applicationContext as SensingApplication).uploadConfiguration
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
