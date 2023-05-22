package com.google.android.sensory.example

import android.app.Application
import android.content.Context
import com.google.android.sensory.sensing_sdk.SensingEngine
import com.google.android.sensory.sensing_sdk.SensingEngineProvider
import com.google.android.sensory.sensing_sdk.UploadConfiguration
import java.util.Properties

class SensingApplication: Application() {
  private val sensingEngine by lazy { constructSensingEngine() }
  private val uploadConfiguration by lazy { constructUploadConfiguration() }

  private fun constructSensingEngine(): SensingEngine {
    SensingEngineProvider.init(uploadConfiguration)
    return SensingEngineProvider.getOrCreateSensingEngine(
      applicationContext,
      false
    )
  }


  private fun constructUploadConfiguration(): UploadConfiguration{
    val properties = Properties().apply { load(applicationContext.assets.open("local.properties")) }
    return UploadConfiguration(
      HOST = properties.getProperty("HOST"),
      bucketName = properties.getProperty("BUCKET_NAME"),
      user = properties.getProperty("USER"),
      password = properties.getProperty("PASSWORD")
    )
  }

  companion object{
    fun sensingEngine(context: Context) = (context.applicationContext as SensingApplication).sensingEngine

    fun uploadConfiguration(context: Context) = (context.applicationContext as SensingApplication).uploadConfiguration
  }
}