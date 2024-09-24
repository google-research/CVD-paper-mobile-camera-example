/*
 * Copyright 2023-2024 Google LLC
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

import com.google.android.sensing.upload.BlobstoreService

/**
 * A configuration which describes the database setup and error recovery.
 *
 * Database encryption is only available on API 23 or above. If enableEncryptionIfSupported is true,
 * Sensing SDK will only enable database encryption on API 23 or above.
 *
 * WARNING: Your app may try to decrypt an unencrypted database from a device which was previously
 * on API 22 but later upgraded to API 23. When this happens, an [IllegalStateException] is thrown.
 */
data class SensingEngineConfiguration(
  val databaseConfiguration: DatabaseConfiguration = DatabaseConfiguration(),
  val serverConfiguration: ServerConfiguration? = null,
) {
  interface Provider {
    fun getSensingEngineConfiguration(): SensingEngineConfiguration
  }
}

data class DatabaseConfiguration(
  val inMemory: Boolean = false,
  val enableEncryption: Boolean = true,
  val databaseErrorStrategy: DatabaseErrorStrategy = DatabaseErrorStrategy.UNSPECIFIED
) {
  enum class DatabaseErrorStrategy {
    /**
     * If unspecified, all database errors will be propagated to the call site. The caller shall
     * handle the database error on a case-by-case basis.
     */
    UNSPECIFIED,

    /**
     * If a database error occurs at open, automatically recreate the database.
     *
     * This strategy is NOT respected when opening a previously unencrypted database with an
     * encrypted configuration or vice versa. An [IllegalStateException] is thrown instead.
     */
    RECREATE_AT_OPEN
  }
}

/** A configuration to provide necessary params for network connection. */
data class ServerConfiguration(
  /** An instance of [BlobstoreService] provided by the application. */
  val blobstoreService: BlobstoreService? = null,

  /** Url of the remote blob-storage server. */
  val baseUrl: String? = null,

  /** The access url for a blob-storage server can be different. */
  val baseAccessUrl: String? = null,

  /** Bucket name in the blob-storage. */
  val bucketName: String? = null,

  /** A configuration to provide the network connection parameters. */
  val networkConfiguration: NetworkConfiguration = NetworkConfiguration(),
  /**
   * An [Authenticator] for supplying any auth token that may be necessary to communicate with the
   * server
   */
  val authenticator: Authenticator? = null
) {
  init {
    // Ensure that either blobstoreService is provided, or all other fields are present
    if (blobstoreService == null) {
      requireNotNull(baseUrl) { "baseUrl is required when blobstoreService is not provided" }
      requireNotNull(baseAccessUrl) {
        "baseAccessUrl is required when blobstoreService is not provided"
      }
      requireNotNull(bucketName) { "bucketName is required when blobstoreService is not provided" }
      requireNotNull(networkConfiguration) {
        "networkConfiguration is required when blobstoreService is not provided"
      }
    }
  }

  /** A configuration to provide the network connection parameters. */
  data class NetworkConfiguration(
    /** Connection timeout (in seconds). The default in [OkHttpClient] is 10 seconds. */
    val connectionTimeOut: Long = 30,

    /**
     * Write timeout (in seconds) for network connection. The default in [OkHttpClient] is 10
     * seconds.
     */
    val writeTimeOut: Long = 30,

    /**
     * Read timeout (in seconds) for network connection. The default in [OkHttpClient] is 10
     * seconds.
     */
    val readTimeOut: Long = 30,

    /** Uploads should be multi part or not. */
    val isMultiPart: Boolean = true
  )
}
