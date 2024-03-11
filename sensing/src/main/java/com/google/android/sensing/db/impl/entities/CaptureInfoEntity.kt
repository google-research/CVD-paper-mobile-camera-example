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
import com.google.android.sensing.capture.CaptureSettings
import com.google.android.sensing.model.CaptureType
import java.time.Instant

/**
 * TODO Update this for Sensing1.0.
 * * Remove captureType, captureSettings.
 * * Rather store captureRequestType: String, requestType: [CaptureRequest].
 */
@Entity(indices = [Index(value = ["captureFolder"], unique = true)])
/**
 * Information about the capture: Which participant, type of capture, folder data being captured to,
 * and a captureId associated with this capture record. Later we could add capture settings as well.
 */
internal data class CaptureInfoEntity(
  /** Unique id for each capture. */
  @PrimaryKey val captureId: String,

  /**
   * External identifier for which this capture was triggered. When this is not provided by
   * application this is same as [captureId]
   */
  val externalIdentifier: String,

  /** Tracking capture information like the ones below. May include [CaptureSettings] later. */
  val captureType: CaptureType,

  /** Unique folder for each capture. */
  val captureFolder: String,

  /** Time of this capture. */
  val captureTime: Instant,

  /** Different settings involved in this capture. */
  val captureSettings: CaptureSettings,
)
