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

package com.google.android.sensory.fhir_data

import android.net.Uri
import android.view.View
import androidx.core.net.toUri
import java.io.File

class ViewHolderFactoryUtil {
  companion object {
    fun getFirstOrNullImageUri(root: String, fileType: String): Uri? {
      val file = File(root)
      return file.listFiles()?.firstOrNull { it.extension == fileType }?.toUri()
    }
    fun removeUnwantedViews(itemView: View) {
      itemView.findViewById<View>(com.google.android.fhir.datacapture.R.id.prefix).visibility =
        View.GONE
      itemView
        .findViewById<View>(com.google.android.fhir.datacapture.R.id.helpContainer)
        .visibility = View.GONE
      itemView.findViewById<View>(com.google.android.fhir.datacapture.R.id.question).visibility =
        View.GONE
      itemView.findViewById<View>(com.google.android.fhir.datacapture.R.id.file_delete).visibility =
        View.GONE
      itemView
        .findViewById<View>(com.google.android.fhir.datacapture.R.id.photo_delete)
        .visibility = View.GONE
      itemView.findViewById<View>(com.google.android.fhir.datacapture.R.id.item_media).visibility =
        View.GONE
      itemView.findViewById<View>(com.google.android.fhir.datacapture.R.id.error).visibility =
        View.GONE
    }
  }
}
