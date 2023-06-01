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

package com.google.android.sensory.example.fhir_data

import android.net.Uri
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.net.toUri
import com.bumptech.glide.Glide
import com.google.android.fhir.datacapture.views.MediaView
import com.google.android.material.textview.MaterialTextView
import java.io.File

class PhotoViewHolderFactoryUtil(
  private val photoPreview: ConstraintLayout,
  private val photoThumbnail: ImageView,
  private val photoTitle: TextView,
  private val context: AppCompatActivity
) {

  fun displayPreview(
    attachmentTitle: String,
    attachmentByteArray: ByteArray? = null,
    attachmentUri: Uri? = null,
  ) {
    if (attachmentByteArray != null) {
      loadPhotoPreview(attachmentByteArray, attachmentTitle)
    } else if (attachmentUri != null) {
      loadPhotoPreview(attachmentUri, attachmentTitle)
    }
  }

  private fun loadPhotoPreview(byteArray: ByteArray, title: String) {
    photoPreview.visibility = View.VISIBLE
    Glide.with(context).load(byteArray).into(photoThumbnail)
    photoTitle.text = title
  }

  private fun loadPhotoPreview(uri: Uri, title: String) {
    photoPreview.visibility = View.VISIBLE
    Glide.with(context).load(uri).into(photoThumbnail)
    photoTitle.text = title
  }

  fun clearPhotoPreview() {
    photoPreview.visibility = View.GONE
    Glide.with(context).clear(photoThumbnail)
    photoTitle.text = ""
  }
  companion object {
    fun getFirstOrNullImageUri(root: String, fileType: String): Uri? {
      val file = File(root)
      return file.listFiles()?.firstOrNull { it.extension == fileType }?.toUri()
    }

    fun removeUnwantedViews(itemView: View) {
      itemView
        .findViewById<MaterialTextView>(com.google.android.fhir.datacapture.R.id.prefix)
        .visibility = View.GONE
      itemView
        .findViewById<MaterialTextView>(com.google.android.fhir.datacapture.R.id.question)
        .visibility = View.GONE
      itemView
        .findViewById<LinearLayout>(com.google.android.fhir.datacapture.R.id.helpContainer)
        .visibility = View.GONE
      itemView
        .findViewById<Button>(com.google.android.fhir.datacapture.R.id.file_delete)
        .visibility = View.GONE
      itemView
        .findViewById<Button>(com.google.android.fhir.datacapture.R.id.photo_delete)
        .visibility = View.GONE
      itemView
        .findViewById<MediaView>(com.google.android.fhir.datacapture.R.id.item_media)
        .visibility = View.GONE
      itemView
        .findViewById<MaterialTextView>(com.google.android.fhir.datacapture.R.id.error)
        .visibility = View.GONE
    }
  }
}
