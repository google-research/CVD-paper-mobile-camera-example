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

import java.util.UUID

/**
 * Base class representing a capture request with common properties.
 *
 * @param externalIdentifier App identifier for this capture request.
 * @param outputFolder The destination folder where captured output will be saved.
 * @param outputFormat The output format (e.g., "jpeg", "video/mp4v-es")
 * @param outputTitle Label to display in place of the data.
 */
open class CaptureRequest(
  open val externalIdentifier: String,
  open val outputFolder: String,
  open val outputFormat: String,
  open val outputTitle: String,
  val captureId: String = UUID.randomUUID().toString(),
)
