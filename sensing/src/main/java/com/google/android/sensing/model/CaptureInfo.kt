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

package com.google.android.sensing.model

import com.google.android.sensing.capture.CaptureSettings
import java.util.Date

/** Data class equivalent to [CaptureInfoEntity] for usage outside database. */
data class CaptureInfo(
  val participantId: String,
  val captureType: CaptureType,
  val captureFolder: String,
  var captureTime: Date? = null,
  var captureId: String? = null,
  /** This is not persisted in database for now */
  val captureSettings: CaptureSettings,
  val recapture: Boolean? = false,
)
