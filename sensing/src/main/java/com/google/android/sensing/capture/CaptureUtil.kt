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

import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
  }
}
