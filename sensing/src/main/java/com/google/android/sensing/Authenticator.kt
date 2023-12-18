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

@WorkerThread
/**
 * [SensingEngine] depends on the developer app to handle user's authentication. The developer
 * application may provide the implementation during the [SensingEngine] initial setup.
 *
 * We provide 2 interfaces:
 * 1. BasicAuthenticator: Accepts access-key (username) and secret-key (password) directly
 * 2. TokenAuthenticator: Accepts token and other details required for minio server to generate
 * temporary credentials, ie access and secret keys.
 */
interface Authenticator

interface BasicAuthenticator : Authenticator {
  /** @return User name for the engine to make requests to the blob-storage on user's behalf. */
  fun getUserName(): String

  /** @return Blob-storage account password for this user. */
  fun getPassword(): String
}

interface TokenAuthenticator : Authenticator {
  fun getToken(): String

  fun getRoleArn(): String

  fun getDurationSeconds(): Int
}
