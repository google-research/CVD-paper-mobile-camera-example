package com.google.android.sensory.example


import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.ColorSpaceTransform
import android.hardware.camera2.params.RggbChannelVector
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.google.android.sensory.R
import com.google.android.sensory.example.data.AppSensorDataUploadWorker
import com.google.android.sensory.sensing_sdk.capture.model.CaptureSettings
import com.google.android.sensory.sensing_sdk.model.CaptureType
import com.google.android.sensory.sensing_sdk.model.SensorType
import com.google.android.sensory.sensing_sdk.upload.UploadSync
import java.util.Arrays

class MainActivity : AppCompatActivity() {
  var participantId: String? = null
  var fingernailsFilePath: String? = null
  var conjunctivaFilePath: String? = null
  private var permissionsRequestCount = 0
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    // Request permissions.
    if (!hasPermissions()) {
      permissionsRequestCount = 0
      ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
    } else {
      startHomeFragment()
    }
  }

  private fun startHomeFragment() {
    // UploadSync.enqueueUploadPeriodicWork<AppSensorDataUploadWorker>(applicationContext)
    participantId = ""
    val homeFragment: Fragment = HomeFragment()
    supportFragmentManager.beginTransaction().replace(R.id.flContainer, homeFragment).commit()
  }

  private fun hasPermissions(): Boolean {
    return !Arrays.stream(REQUIRED_PERMISSIONS).anyMatch {
      ActivityCompat.checkSelfPermission(applicationContext, it) != PackageManager.PERMISSION_GRANTED
    }
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray,
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (REQUEST_CODE_PERMISSIONS == requestCode) {
      if (grantResults.size < REQUIRED_PERMISSIONS.size || grantResults[0] != PackageManager.PERMISSION_GRANTED || grantResults[1] != PackageManager.PERMISSION_GRANTED) {
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
              DialogInterface.OnClickListener { dialog, which -> finishAndRemoveTask() })
            .setCancelable(false)
          val dialog = builder.create()
          dialog.show()
        }
      } else {
        startHomeFragment()
      }
    }
  }

  companion object {
    private const val MAX_PERMISSIONS_REQUESTS = 10
    private const val REQUEST_CODE_PERMISSIONS = 100
    private val REQUIRED_PERMISSIONS = arrayOf(
      Manifest.permission.CAMERA,
      Manifest.permission.WRITE_EXTERNAL_STORAGE,
      Manifest.permission.READ_EXTERNAL_STORAGE
    )

    // The following version constant should be updated whenever we ship a new version of the app.
    // The version format is composed of two parts vXsY, which stands for version X subversion Y.
    // We do not use "." in the version string to avoid confusion with file suffixes.
    private const val APP_VERSION = "v2s1"
  }
}
