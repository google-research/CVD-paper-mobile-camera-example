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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import com.google.android.fhir.sync.PeriodicSyncConfiguration
import com.google.android.fhir.sync.RepeatInterval
import com.google.android.fhir.sync.Sync
import com.google.android.sensing.upload.SensingUploadSync
import com.google.android.sensory.fhir_data.FhirSyncWorker
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainActivityViewModel(application: Application) : AndroidViewModel(application) {
  init {
    viewModelScope.launch {
      SensingUploadSync.enqueueUploadPeriodicWork(application.applicationContext)
      Sync.periodicSync<FhirSyncWorker>(
        application.applicationContext,
        PeriodicSyncConfiguration(
          syncConstraints = Constraints.Builder().build(),
          repeat = RepeatInterval(interval = 15, timeUnit = TimeUnit.MINUTES)
        )
      )
    }
  }

  fun triggerOneTimeSync() {
    viewModelScope.launch {
      SensingUploadSync.enqueueUploadUniqueWork(getApplication())
      Sync.oneTimeSync<FhirSyncWorker>(getApplication())
    }
  }
}
