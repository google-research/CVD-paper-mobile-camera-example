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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.view.View
import androidx.core.net.toUri
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class ViewHolderFactoryUtil {
  companion object {
    fun getFirstOrNullImageUri(root: File, relativePath: String, fileType: String): Uri? {
      val file = File(root, relativePath)
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

    // Function to rotate the image bitmap based on its Exif information
    fun rotateImageIfRequired(context: Context, bitmap: Bitmap, uri: Uri): Bitmap {
      return try {
        val exif = context.contentResolver.openInputStream(uri)?.let { ExifInterface(it) }

        val rotationInDegrees = when (exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
          ExifInterface.ORIENTATION_ROTATE_90 -> 90
          ExifInterface.ORIENTATION_ROTATE_180 -> 180
          ExifInterface.ORIENTATION_ROTATE_270 -> 270
          else -> 90
        }

        val matrix = Matrix()
        matrix.postRotate(rotationInDegrees.toFloat())
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

      } catch (e: IOException) {
        e.printStackTrace()
        bitmap
      }
    }

    class RotateTransformation(private val context: Context, private val uri: Uri?) : BitmapTransformation() {
      override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int
      ): Bitmap {
        return rotateImageIfRequired(context, toTransform, uri!!)
      }

      override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update((context.packageName + uri.toString()).toByteArray())
      }
    }
  }
}
