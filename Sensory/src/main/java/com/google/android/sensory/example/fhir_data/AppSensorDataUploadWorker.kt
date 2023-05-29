/*
 * Copyright 2022 Google LLC
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

package com.google.android.sensory.example.fhir_data

import android.content.Context
import androidx.work.WorkerParameters
import com.google.android.sensory.example.SensingApplication
import com.google.android.sensory.sensing_sdk.upload.SensorDataUploadWorker

class AppSensorDataUploadWorker(appContext: Context, workerParams: WorkerParameters) :
  SensorDataUploadWorker(appContext, workerParams) {
  override fun getSensingEngine() = SensingApplication.sensingEngine(applicationContext)

  override fun getUploadConfiguration() = SensingApplication.uploadConfiguration(applicationContext)
}
