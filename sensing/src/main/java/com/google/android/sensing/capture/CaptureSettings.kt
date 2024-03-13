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

package com.google.android.sensing.capture

import com.google.android.sensing.model.InternalSensorType

data class CaptureSettings(
  /**
   * The mimetype map for files captured via a sensor. TODO: Need to have default types for each
   * sensor.
   */
  val fileTypeMap: Map<InternalSensorType, String>,
  /** The mimetype map for metadata files captured via a sensor. */
  val metaDataTypeMap: Map<InternalSensorType, String>,
  /**
   * The capture title is appended next to ParticipantID in a filename being produced while
   * capturing.
   */
  val captureTitle: String,
  /** Seconds one wants to record PPG video. */
  val ppgTimer: Int = 0
)
