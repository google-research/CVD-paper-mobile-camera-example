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

package com.google.android.sensing.upload

import com.google.android.sensing.MinioIDPPluginAuthenticator
import io.minio.Xml
import io.minio.credentials.AssumeRoleBaseProvider
import io.minio.credentials.Credentials
import io.minio.messages.ResponseDate
import java.util.Objects
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import org.simpleframework.xml.Element
import org.simpleframework.xml.Namespace
import org.simpleframework.xml.Root

/**
 * Implementation of [io.minio.credentials.Provider] using AssumeRoleWithCustomToken API. Extends
 * and implements [io.minio.credentials.AssumeRoleBaseProvider].
 */
class CustomTokenIdentityProvider(
  okHttpClient: OkHttpClient?,
  private val stsEndpoint: String,
  private val tokenProvider: MinioIDPPluginAuthenticator.TokenProvider,
  private val roleArn: String,
  private val durationSeconds: Int,
) : AssumeRoleBaseProvider(okHttpClient) {

  init {
    Objects.requireNonNull(stsEndpoint, "STS endpoint cannot be empty")
  }

  /** This API is called whenever new credentials are needed. */
  override fun getRequest(): Request {
    val stsHttpUrl = Objects.requireNonNull(stsEndpoint.toHttpUrl(), "Invalid STS endpoint")
    val url =
      newUrlBuilder(
          stsHttpUrl,
          "AssumeRoleWithCustomToken",
          getValidDurationSeconds(durationSeconds),
          null,
          roleArn,
          null
        )
        .addQueryParameter("Token", tokenProvider.getToken())
        .build()

    val emptyRequestBody =
      object : RequestBody() {
        override fun contentType(): MediaType? {
          return "application/octet-stream".toMediaTypeOrNull()
        }

        override fun writeTo(sink: BufferedSink) {}
      }

    return Request.Builder().url(url).method("POST", emptyRequestBody).build()
  }

  override fun getResponseClass(): Class<out Response> {
    return Response::class.java
  }

  override fun parseResponse(response: okhttp3.Response?): io.minio.credentials.Credentials {
    val result =
      Xml.unmarshal(AssumeRoleWithCustomTokenResponse::class.java, response!!.body!!.charStream())
    return result.assumeRoleResult.credentials.toCredentials()
  }

  @Root(name = "AssumeRoleWithCustomTokenResponse", strict = false)
  @Namespace(reference = "https://sts.amazonaws.com/doc/2011-06-15/")
  data class AssumeRoleWithCustomTokenResponse(
    @field:Element(name = "AssumeRoleWithCustomTokenResult")
    var assumeRoleResult: AssumeRoleResult = AssumeRoleResult(),
    @field:Element(name = "ResponseMetadata")
    var responseMetadata: ResponseMetadata = ResponseMetadata()
  )

  data class AssumeRoleResult(
    @field:Element(name = "Credentials") var credentials: Credentials = Credentials(),
    @field:Element(name = "AssumedUser") var assumedUser: String = ""
  )

  data class Credentials(
    @field:Element(name = "AccessKeyId") var accessKeyId: String = "",
    @field:Element(name = "SecretAccessKey") var secretAccessKey: String = "",
    @field:Element(name = "Expiration") var expiration: String = "",
    @field:Element(name = "SessionToken") var sessionToken: String = ""
  ) {
    fun toCredentials() =
      Credentials(accessKeyId, secretAccessKey, sessionToken, ResponseDate.fromString(expiration))
  }

  data class ResponseMetadata(@field:Element(name = "RequestId") var requestId: String = "")
}
