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

package com.google.android.sensing.testing

import android.app.Application
import com.google.android.sensing.DatabaseConfiguration
import com.google.android.sensing.MinioInternalIDPAuthenticator
import com.google.android.sensing.SensingEngineConfiguration
import com.google.android.sensing.ServerConfiguration

class TestApplication : Application(), SensingEngineConfiguration.Provider {
  override fun getSensingEngineConfiguration() =
    SensingEngineConfiguration(
      databaseConfiguration = DatabaseConfiguration(inMemory = true, enableEncryption = false),
      serverConfiguration =
        ServerConfiguration(
          baseUrl = TEST_BASE_URL,
          baseAccessUrl = TEST_BASE_ACCESS_URL,
          bucketName = TEST_BUCKET_NAME,
          authenticator =
            object : MinioInternalIDPAuthenticator() {
              override fun getUserName() = TEST_USER_NAME
              override fun getPassword() = TEST_USER_PASSWORD
            }
        )
    )

  companion object {
    const val TEST_BASE_URL = "TEST-BASE-URL"
    const val TEST_BASE_ACCESS_URL = "TEST-BASE-ACCESS-URL"
    const val TEST_BUCKET_NAME = "TEST-BUCKET-NAME"
    const val TEST_USER_NAME = "TEST-USER-NAME"
    const val TEST_USER_PASSWORD = "TEST-USER-PASSWORD"
  }
}
