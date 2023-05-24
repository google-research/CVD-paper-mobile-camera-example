package com.google.android.sensory.example

import android.app.Application
import android.content.Context
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.FhirEngineProvider
import com.google.android.fhir.datacapture.DataCaptureConfig
import com.google.android.fhir.datacapture.QuestionnaireFragment
import com.google.android.sensory.example.data.ConjunctivaPhotoSensorCaptureViewHolderFactory
import com.google.android.sensory.example.data.FingernailsPhotoSensorCaptureViewHolderFactory
import com.google.android.sensory.example.data.PPGSensorCaptureViewHolderFactory
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
    dataCaptureConfig =
      DataCaptureConfig().apply {
        // urlResolver = ReferenceUrlResolver(this@SensingApplication as Context)
        // xFhirQueryResolver = XFhirQueryResolver { fhirEngine.search(it) }
      }
  }

  private fun constructFhirEngine(): FhirEngine {
    return FhirEngineProvider.getInstance(this)
  }

  private fun constructSensingEngine(): SensingEngine {
    SensingEngineProvider.init(uploadConfiguration)
    return SensingEngineProvider.getOrCreateSensingEngine(
      applicationContext,
      false
    )
  }


  private fun constructUploadConfiguration(): UploadConfiguration {
    val properties = Properties().apply { load(applicationContext.assets.open("local.properties")) }
    return UploadConfiguration(
      HOST = properties.getProperty("HOST"),
      bucketName = properties.getProperty("BUCKET_NAME"),
      user = properties.getProperty("USER"),
      password = properties.getProperty("PASSWORD")
    )
  }

  companion object {

    fun fhirEngine(context: Context) = (context.applicationContext as SensingApplication).fhirEngine
    fun sensingEngine(context: Context) =
      (context.applicationContext as SensingApplication).sensingEngine

    fun uploadConfiguration(context: Context) =
      (context.applicationContext as SensingApplication).uploadConfiguration
  }

  override fun getDataCaptureConfig(): DataCaptureConfig {
    return DataCaptureConfig(questionnaireItemViewHolderFactoryMatchersProviderFactory = { tag ->
      "sensor_capture"
      object : QuestionnaireFragment.QuestionnaireItemViewHolderFactoryMatchersProvider() {

        override fun get(): List<QuestionnaireFragment.QuestionnaireItemViewHolderFactoryMatcher> {
          return listOf(
            QuestionnaireFragment.QuestionnaireItemViewHolderFactoryMatcher(
              FingernailsPhotoSensorCaptureViewHolderFactory
            ) { questionnaireItem ->
              questionnaireItem.getExtensionByUrl(FingernailsPhotoSensorCaptureViewHolderFactory.WIDGET_EXTENSION)
                .let {
                  if (it == null) false else it.value.toString() == FingernailsPhotoSensorCaptureViewHolderFactory.WIDGET_TYPE
                }
            },
            QuestionnaireFragment.QuestionnaireItemViewHolderFactoryMatcher(
              ConjunctivaPhotoSensorCaptureViewHolderFactory
            ) { questionnaireItem ->
              questionnaireItem.getExtensionByUrl(ConjunctivaPhotoSensorCaptureViewHolderFactory.WIDGET_EXTENSION)
                .let {
                  if (it == null) false else it.value.toString() == ConjunctivaPhotoSensorCaptureViewHolderFactory.WIDGET_TYPE
                }
            },
            QuestionnaireFragment.QuestionnaireItemViewHolderFactoryMatcher(
              PPGSensorCaptureViewHolderFactory
            ) {
                questionnaireItem,
              ->
              questionnaireItem.getExtensionByUrl(PPGSensorCaptureViewHolderFactory.WIDGET_EXTENSION)
                .let {
                  if (it == null) false
                  else it.value.toString() == PPGSensorCaptureViewHolderFactory.WIDGET_TYPE
                }
            }
          )
        }

      }
    })
  }
}