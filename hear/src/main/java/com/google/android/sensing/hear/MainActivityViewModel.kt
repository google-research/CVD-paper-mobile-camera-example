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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.android.sensing.upload.SensingUploadSync
import com.google.android.sensing.upload.SyncUploadState
import com.google.cloud.aiplatform.v1.EndpointName
import com.google.cloud.aiplatform.v1.PredictRequest
import com.google.cloud.aiplatform.v1.PredictionServiceClient
import com.google.protobuf.ListValue
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivityViewModel(application: Application, private val state: SavedStateHandle) :
  AndroidViewModel(application) {

  private val _syncUploadStateFlow = MutableSharedFlow<SyncUploadState>()

  val syncUploadState: Flow<SyncUploadState>
    get() = _syncUploadStateFlow

  private val _permissionsAvailable = MutableSharedFlow<Boolean>()
  val permissionsAvailable: Flow<Boolean>
    get() = _permissionsAvailable

  fun setPermissionsAvailability(b: Boolean) {
    viewModelScope.launch { _permissionsAvailable.emit(b) }
  }

  fun triggerOneTimeSync() {
    viewModelScope.launch {
      SensingUploadSync.oneTimeSyncUpload(getApplication()).collect {
        _syncUploadStateFlow.emit(it)
      }
    }
  }
}
