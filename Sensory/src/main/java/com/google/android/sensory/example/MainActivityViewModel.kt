package com.google.android.sensory.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.sensory.example.fhir_data.AppSensorDataUploadWorker
import com.google.android.sensory.sensing_sdk.upload.UploadSync
import kotlinx.coroutines.launch

class MainActivityViewModel(application: Application) : AndroidViewModel(application) {
  init {
    viewModelScope.launch {
      UploadSync.enqueueUploadPeriodicWork<AppSensorDataUploadWorker>(application.applicationContext)
    }
  }

  fun triggerOneTimeSync() {
    viewModelScope.launch {
      UploadSync.enqueueUploadUniqueWork<AppSensorDataUploadWorker>(getApplication())
    }
  }
}