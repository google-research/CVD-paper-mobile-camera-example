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

package com.google.android.sensing.db.impl.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.android.sensing.capture.CaptureRequest
import java.time.Instant

@Entity(indices = [Index(value = ["captureFolder"], unique = true)])
/**
 * Information about the capture: id, request, time, output-folder, and an external-identifier for
 * application's reference.
 */
internal data class CaptureInfoEntity(
  /** Unique id for each capture. */
  @PrimaryKey val captureId: String,

  /**
   * External identifier for which this capture was triggered. When this is not provided by
   * application this is same as [captureId]
   */
  val externalIdentifier: String,

  /** [CaptureRequest] type to deserialize back the request. */
  val captureRequestType: String,

  /** Serialized [CaptureRequest] used in a capture */
  val captureRequest: String,

  /** Unique folder for each capture. */
  val captureFolder: String,

  /** Time of this capture. */
  val captureTime: Instant,
)
