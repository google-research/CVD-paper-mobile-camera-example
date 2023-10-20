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

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.sensing.db.impl.DatabaseConfig
import com.google.android.sensing.db.impl.DatabaseImpl
import com.google.android.sensing.impl.SensingEngineImpl
import com.google.android.sensing.upload.BlobstoreService
import io.minio.MinioAsyncClient
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

object SensingEngineProvider {
  private var sensingEngineConfiguration: SensingEngineConfiguration? = null
  private var sensingEngine: SensingEngine? = null
  private var blobstoreService: BlobstoreService? = null

  fun init(sensingEngineConfiguration: SensingEngineConfiguration) {
    this.sensingEngineConfiguration = sensingEngineConfiguration
  }

  @SuppressLint("UnsafeOptInUsageError")
  fun getOrCreateSensingEngine(context: Context): SensingEngine {
    if (sensingEngine == null) {
      val configuration = checkNotNull(sensingEngineConfiguration)
      val database =
        DatabaseImpl(
          context,
          DatabaseConfig(
            configuration.enableEncryptionIfSupported,
            configuration.databaseErrorStrategy
          )
        )
      sensingEngine = SensingEngineImpl(database, context, configuration.serverConfiguration)
    }
    return sensingEngine!!
  }

  fun getBlobStoreService(): BlobstoreService {
    if (blobstoreService == null) {
      with(sensingEngineConfiguration!!.serverConfiguration) {
        blobstoreService =
          BlobstoreService(
            MinioAsyncClient.builder()
              .endpoint(baseUrl)
              .credentials(authenticator!!.getUserName(), authenticator.getPassword())
              .httpClient(
                OkHttpClient.Builder()
                  .connectTimeout(networkConfiguration.connectionTimeOut, TimeUnit.SECONDS)
                  .writeTimeout(networkConfiguration.writeTimeOut, TimeUnit.SECONDS)
                  .build()
              )
              .build()
          )
      }
    }
    return blobstoreService!!
  }
}

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
  val enableEncryptionIfSupported: Boolean = false,
  val databaseErrorStrategy: DatabaseErrorStrategy = DatabaseErrorStrategy.UNSPECIFIED,
  val serverConfiguration: ServerConfiguration
)

enum class DatabaseErrorStrategy {
  /**
   * If unspecified, all database errors will be propagated to the call site. The caller shall
   * handle the database error on a case-by-case basis.
   */
  UNSPECIFIED,

  /**
   * If a database error occurs at open, automatically recreate the database.
   *
   * This strategy is NOT respected when opening a previously unencrypted database with an encrypted
   * configuration or vice versa. An [IllegalStateException] is thrown instead.
   */
  RECREATE_AT_OPEN
}

/** A configuration to provide necessary params for network connection. */
data class ServerConfiguration(
  /** Url of the remote blob-storage server. */
  val baseUrl: String,
  /** The access url for a blob-storage server can be different. */
  val baseAccessUrl: String,
  /** Bucket name in the blob-storage. */
  val bucketName: String,
  /** A configuration to provide the network connection parameters. */
  val networkConfiguration: NetworkConfiguration = NetworkConfiguration(),
  /**
   * An [Authenticator] for supplying any auth token that may be necessary to communicate with the
   * server
   */
  val authenticator: Authenticator? = null
) {
  fun getBucketUrl() = "$baseAccessUrl/$bucketName"
}

/** A configuration to provide the network connection parameters. */
data class NetworkConfiguration(
  /** Connection timeout (in seconds). The default is 10 seconds. */
  val connectionTimeOut: Long = 10,
  /** Write timeout (in seconds) for network connection. The default is 10 seconds. */
  val writeTimeOut: Long = 10,
  /** Uploads should be multi part or not. */
  val isMultiPart: Boolean = true
)
