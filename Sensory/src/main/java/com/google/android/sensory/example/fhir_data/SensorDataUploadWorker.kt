package com.google.android.sensory.example.fhir_data

import android.content.Context
import androidx.work.WorkerParameters
import com.google.android.sensory.example.SensingApplication
import com.google.android.sensory.sensing_sdk.upload.SensorDataUploadWorker

class AppSensorDataUploadWorker(appContext: Context, workerParams: WorkerParameters): SensorDataUploadWorker(appContext, workerParams) {
  override fun getSensingEngine() = SensingApplication.sensingEngine(applicationContext)

  override fun getUploadConfiguration() = SensingApplication.uploadConfiguration(applicationContext)

}