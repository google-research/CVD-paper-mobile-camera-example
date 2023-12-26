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

package com.google.android.sensing

import androidx.annotation.WorkerThread
import com.google.android.sensing.upload.CustomTokenIdentityProvider
import io.minio.credentials.Credentials
import io.minio.credentials.Provider
import okhttp3.OkHttpClient

@WorkerThread
/**
 * Sensing library depends on the developer app to handle user's authentication. The developer
 * application may provide the implementation during the initial setup.
 *
 * Application may use one of the abstract implementations of this interface or provide its own
 * implementation. The interface returns a [io.minio.credentials.Provider] which is used to build a
 * [MinioAsyncClient].
 * 1. [MinioInternalIDPAuthenticator]: Implement this if deployment supports built-in IDP. Accepts
 * access-key (username) and secret-key (password) directly.
 * 2. [MinioIDPPluginAuthenticator]: Implement this if deployment supports external IDP. Accepts
 * [OkHttpClient], [MinioIDPPluginAuthenticator.TokenProvider], roleArn, durationSeconds and
 * stsEndpoint to create a [CustomTokenIdentityProvider].
 */
interface Authenticator {
  fun getCredentialsProvider(): Provider
}

abstract class MinioInternalIDPAuthenticator : Authenticator {
  /** @return User name for the engine to make requests to the blob-storage on user's behalf. */
  abstract fun getUserName(): String
  /** @return Blob-storage account password for this user. */
  abstract fun getPassword(): String
  override fun getCredentialsProvider() = Provider {
    Credentials(getUserName(), getPassword(), null, null)
  }
}

abstract class MinioIDPPluginAuthenticator : Authenticator {
  /** Override this to provide your own [OkHttpClient]. */
  open fun getOkHttpClient(): OkHttpClient? = null

  /** Provide [TokenProvider] instance that is used to fetch fresh token. */
  abstract fun getTokenProvider(): TokenProvider

  /**
   * Specify the ARN of the IAM role that MinIO should assume. Refer
   * https://min.io/docs/minio/linux/reference/minio-mc/mc-event-list.html#mc.event.ls.ARN
   */
  abstract fun getRoleArn(): String

  /**
   * Specify the number of seconds after which the temporary credentials expire. Defaults to 3600.
   *
   * Note: Minimum is 900 seconds | Maximum is 604800 seconds.
   */
  abstract fun getDurationSeconds(): Int

  /** Provide STS endpoint for AssumeRoleWithCustomToken API. */
  abstract fun getStsEndpoint(): String

  /** Implement this functional interface to provide fresh token when invoked. */
  fun interface TokenProvider {
    fun getToken(): String
  }

  override fun getCredentialsProvider() =
    CustomTokenIdentityProvider(
      getOkHttpClient(),
      getStsEndpoint(),
      getTokenProvider(),
      getRoleArn(),
      getDurationSeconds()
    )
}
