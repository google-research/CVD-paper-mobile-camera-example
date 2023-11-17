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

package com.google.android.sensing.capture

sealed class SensorCaptureResult {

  data class Started(val captureId: String) : SensorCaptureResult()

  data class CaptureComplete(val captureId: String) : SensorCaptureResult()

  data class CaptureInfoCreated(val captureId: String) : SensorCaptureResult()

  data class ResourceInfoCreated(val resourceInfoId: String) : SensorCaptureResult()

  data class UploadRequestCreated(val uploadRequestId: String) : SensorCaptureResult()

  data class ResourcesStored(val captureId: String) : SensorCaptureResult()

  data class Failed(val captureId: String, val t: Throwable) : SensorCaptureResult()
}
