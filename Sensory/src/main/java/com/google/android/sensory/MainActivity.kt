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

package com.google.android.sensory

import android.Manifest
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.util.Arrays

class MainActivity : AppCompatActivity() {
  private var permissionsRequestCount = 0
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    // Request permissions.
    if (!hasPermissions()) {
      permissionsRequestCount = 0
      ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
    }
  }

  private fun hasPermissions(): Boolean {
    return !Arrays.stream(REQUIRED_PERMISSIONS).anyMatch {
      ActivityCompat.checkSelfPermission(applicationContext, it) !=
        PackageManager.PERMISSION_GRANTED
    }
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray,
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (REQUEST_CODE_PERMISSIONS == requestCode) {
      if (grantResults.size < REQUIRED_PERMISSIONS.size ||
          grantResults.any { it != PackageManager.PERMISSION_GRANTED }
      ) {
        if (permissionsRequestCount < MAX_PERMISSIONS_REQUESTS) {
          // Retry getting permissions.
          permissionsRequestCount++
          ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        } else {
          // Too many attempts, alert and quit.
          val builder = AlertDialog.Builder(this)
          builder
            .setTitle(R.string.permission_error_dialog_title)
            .setMessage(R.string.permission_error_dialog_message)
            .setPositiveButton(
              R.string.acknowledge,
              DialogInterface.OnClickListener { dialog, which -> finishAndRemoveTask() }
            )
            .setCancelable(false)
          val dialog = builder.create()
          dialog.show()
        }
      }
    }
  }

  companion object {
    private const val MAX_PERMISSIONS_REQUESTS = 10
    private const val REQUEST_CODE_PERMISSIONS = 100
    private val REQUIRED_PERMISSIONS =
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
        arrayOf(
          Manifest.permission.CAMERA,
          Manifest.permission.WRITE_EXTERNAL_STORAGE,
          Manifest.permission.READ_EXTERNAL_STORAGE
        )
      else
        arrayOf(
          Manifest.permission.CAMERA,
          // Following are needed for Android API 33 to access files generated out of camera
          Manifest.permission.READ_MEDIA_AUDIO,
          Manifest.permission.READ_MEDIA_VIDEO
        )
  }
}
