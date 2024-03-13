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
import com.google.android.sensing.SensorManager
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.aiplatform.v1.EndpointName
import com.google.cloud.aiplatform.v1.PredictionServiceClient
import com.google.cloud.aiplatform.v1.PredictionServiceSettings
import timber.log.Timber

class HearApplication : Application() {

  override fun onCreate() {
    super.onCreate()
    if (BuildConfig.DEBUG) {
      Timber.plant(Timber.DebugTree())
    }
  }

  private val sensorManager by lazy { constructSensorManager() }
  private val predictionServiceClient by lazy { constructPredictionServiceClient() }
  private val endpointName by lazy { constructEndpointName() }

  private fun constructSensorManager() = SensorManager.getInstance(applicationContext)

  private fun constructPredictionServiceClient(): PredictionServiceClient {
    val credentialsInputStream = applicationContext.assets.open("credentials.json")
    val sourceCredentials: GoogleCredentials =
      GoogleCredentials.fromStream(credentialsInputStream)
        .createScoped(listOf("https://www.googleapis.com/auth/cloud-platform"))
    val predictionServiceSettings: PredictionServiceSettings =
      PredictionServiceSettings.newBuilder()
        .setEndpoint(TODO())
        .setCredentialsProvider { sourceCredentials }
        .build()
    return PredictionServiceClient.create(predictionServiceSettings)
  }

  private fun constructEndpointName(): EndpointName {
    val projectId = TODO()
    val endpointId = TODO()
    val location = TODO()
    return EndpointName.of(projectId, location, endpointId)
  }

  companion object {
    fun getSensorManager(context: Context) =
      (context.applicationContext as HearApplication).sensorManager
    fun getPredictionServiceClient(context: Context) =
      (context.applicationContext as HearApplication).predictionServiceClient
    fun getEndpointName(context: Context) =
      (context.applicationContext as HearApplication).endpointName
  }
}
