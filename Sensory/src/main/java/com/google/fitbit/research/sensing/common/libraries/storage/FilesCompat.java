/*
 * Copyright 2022 Google LLC
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

package com.google.fitbit.research.sensing.common.libraries.storage;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.os.Build;
import com.google.errorprone.annotations.CheckReturnValue;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;

/** Helper methods for {@link Files} compatible with older Android versions. */
@CheckReturnValue
public final class FilesCompat {

  private FilesCompat() {}

  public static BufferedWriter newBufferedWriter(File file) throws IOException {
    return newBufferedWriter(file, UTF_8);
  }

  public static BufferedWriter newBufferedWriter(File file, Charset charset) throws IOException {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      // Only available on API >= 26
      // https://developer.android.com/reference/java/nio/file/Files#newBufferedWriter(java.nio.file.Path,%20java.nio.charset.Charset,%20java.nio.file.OpenOption...)
      return Files.newBufferedWriter(file.toPath(), charset);
    } else {
      return new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), charset));
    }
  }

  public static OutputStream newOutputStream(File file) throws IOException {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      // Only available on API >= 26
      // https://developer.android.com/reference/java/nio/file/Files#newOutputStream(java.nio.file.Path,%20java.nio.file.OpenOption...)
      return Files.newOutputStream(file.toPath());
    } else {
      return new FileOutputStream(file);
    }
  }
}
