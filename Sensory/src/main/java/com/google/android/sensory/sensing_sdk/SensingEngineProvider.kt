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

package com.google.android.sensory.sensing_sdk

import android.content.Context
import com.google.android.sensory.sensing_sdk.db.impl.DatabaseImpl
import com.google.android.sensory.sensing_sdk.impl.SensingEngineImpl

object SensingEngineProvider {
  private lateinit var uploadConfiguration: UploadConfiguration
  private var sensingEngine: SensingEngine? = null
  fun init(uploadConfiguration: UploadConfiguration) {
    this.uploadConfiguration = uploadConfiguration
  }

  fun getOrCreateSensingEngine(context: Context, enableDatabaseEncryption: Boolean): SensingEngine {
    if (sensingEngine == null) {
      val database = DatabaseImpl(context, enableDatabaseEncryption)
      sensingEngine = SensingEngineImpl(database, context, uploadConfiguration)
    }
    return sensingEngine!!
  }
}
