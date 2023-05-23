package com.google.android.sensory.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.sensory.example.data.AppSensorDataUploadWorker
import com.google.android.sensory.sensing_sdk.upload.UploadSync
import kotlinx.coroutines.launch

class MainActivityViewModel(application: Application) : AndroidViewModel(application)  {
  fun triggerOneTimeSync() {
    viewModelScope.launch {
      UploadSync.enqueueUploadUniqueWork<AppSensorDataUploadWorker>(getApplication())
    }
  }
}