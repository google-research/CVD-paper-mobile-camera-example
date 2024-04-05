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

import android.graphics.ImageFormat
import android.graphics.YuvImage
import android.media.Image
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.min

class CaptureUtil {
  companion object {
    fun zipDirectory(sourceDirPath: Path, zipPath: Path) {
      ZipOutputStream(BufferedOutputStream(FileOutputStream(zipPath.toFile()))).use { zipOut ->
        Files.walk(sourceDirPath)
          .filter { path -> !Files.isDirectory(path) } // Exclude directories from the initial walk
          .forEach { path ->
            val zipEntry = ZipEntry(sourceDirPath.relativize(path).toString())
            zipOut.putNextEntry(zipEntry)
            Files.copy(path, zipOut)
            zipOut.closeEntry()
          }
      }
    }

    /**
     * Convert [android.media.Image] to [YuvImage]. Supports only [ImageFormat.YUV_420_888]. Code
     * source - https://gist.github.com/mebjas/c0f1a147b4de35fa8cd3b5364aab4a2d
     */
    fun toYuvImage(image: Image): YuvImage {
      if (image.format != ImageFormat.YUV_420_888) {
        error("Expected image with format YUV_420_888, got ${image.format}")
      }

      val yPlane = image.planes[0]
      val uPlane = image.planes[1]
      val vPlane = image.planes[2]
      val yBuffer = yPlane.buffer
      val uBuffer = uPlane.buffer
      val vBuffer = vPlane.buffer
      yBuffer.rewind()
      uBuffer.rewind()
      vBuffer.rewind()
      val ySize = yBuffer.remaining()
      var position = 0
      val nv21 = ByteArray(ySize + image.width * image.height / 2)

      // Add the full y buffer to the array. If rowStride > 1, some padding may be skipped.
      for (row in 0 until image.height) {
        yBuffer[nv21, position, image.width]
        position += image.width
        yBuffer.position(min(ySize, yBuffer.position() - image.width + yPlane.rowStride))
      }
      val chromaHeight = image.height / 2
      val chromaWidth = image.width / 2
      val vRowStride = vPlane.rowStride
      val uRowStride = uPlane.rowStride
      val vPixelStride = vPlane.pixelStride
      val uPixelStride = uPlane.pixelStride

      // Interleave the u and v frames, filling up the rest of the buffer. Use two line buffers to
      // perform faster bulk gets from the byte buffers.
      val vLineBuffer = ByteArray(vRowStride)
      val uLineBuffer = ByteArray(uRowStride)
      for (row in 0 until chromaHeight) {
        vBuffer.get(vLineBuffer, 0, min(vRowStride, vBuffer.remaining()))
        uBuffer.get(uLineBuffer, 0, min(uRowStride, uBuffer.remaining()))
        var vLineBufferPosition = 0
        var uLineBufferPosition = 0
        for (col in 0 until chromaWidth) {
          nv21[position++] = vLineBuffer[vLineBufferPosition]
          nv21[position++] = uLineBuffer[uLineBufferPosition]
          vLineBufferPosition += vPixelStride
          uLineBufferPosition += uPixelStride
        }
      }
      return YuvImage(nv21, ImageFormat.NV21, image.width, image.height, /*strides=*/ null)
    }
  }
}
