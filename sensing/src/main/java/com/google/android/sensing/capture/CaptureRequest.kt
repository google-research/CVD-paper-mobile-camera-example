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

package com.google.android.sensing.capture

import android.media.MediaRecorder

sealed class CaptureRequest(
  open val externalIdentifier: String,
  open val outputFolder: String,
  open val outputFormat: String,
  open val outputTitle: String,
)

sealed class CameraCaptureRequest(
  override val externalIdentifier: String,
  override val outputFolder: String,
  override val outputFormat: String,
  override val outputTitle: String,
  open val compressionQuality: Int = 100,
  open val bufferCapacity: Int = 0,
  open val maxDataCount: Int? = null,
) : CaptureRequest(externalIdentifier, outputFolder, outputFormat, outputTitle) {

  data class ImageRequest(
    override val externalIdentifier: String,
    override val outputTitle: String,
    override val outputFolder: String,
    override val outputFormat: String = "jpeg",
    override val compressionQuality: Int = 100,
  ) :
    CameraCaptureRequest(
      externalIdentifier,
      outputFolder,
      outputFormat,
      outputTitle,
      compressionQuality,
      maxDataCount = 1
    )

  data class ImageStreamRequest(
    override val externalIdentifier: String,
    override val outputTitle: String,
    override val outputFolder: String,
    override val outputFormat: String = "jpeg",
    override val compressionQuality: Int = 100,
    override val bufferCapacity: Int,
    override val maxDataCount: Int? = null,
  ) :
    CameraCaptureRequest(
      externalIdentifier,
      outputFolder,
      outputFormat,
      outputTitle,
      compressionQuality,
      bufferCapacity,
      maxDataCount
    )

  // Not used currently
  data class VideoRequest(
    override val externalIdentifier: String,
    override val outputFolder: String,
    override val outputFormat: String = "video/mp4v-es",
    override val outputTitle: String,
    override val compressionQuality: Int = 100,
    override val bufferCapacity: Int,
    override val maxDataCount: Int? = null,
    val videoEncoder: Int? = MediaRecorder.VideoEncoder.DEFAULT,
    val audioEncoder: Int? = MediaRecorder.AudioEncoder.DEFAULT,
  ) :
    CameraCaptureRequest(
      externalIdentifier,
      outputFolder,
      outputFormat,
      outputTitle,
      compressionQuality,
      bufferCapacity,
      maxDataCount
    )
}

// Not used currently
data class AudioCaptureRequest(
  override val externalIdentifier: String,
  override val outputFolder: String,
  override val outputFormat: String = "audio/3gpp",
  override val outputTitle: String,
  val audioSource: Int,
  val audioEncoder: Int,
) : CaptureRequest(externalIdentifier, outputFolder, outputFormat, outputTitle)
