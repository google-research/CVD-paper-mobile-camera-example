package com.google.android.sensory.example


import android.Manifest
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.android.sensory.R
import java.io.File


/**  */
class MainActivity : AppCompatActivity(), IListener {
  private var fragmentManager: FragmentManager? = null
  var participantId: String? = null
  var fingernailsFilePath: String? = null
  var conjunctivaFilePath: String? = null
  private var permissionsRequestCount = 0
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    // Request permissions.
    if (!hasPermissions(getApplicationContext())) {
      permissionsRequestCount = 0
      ActivityCompat.requestPermissions(
        this,
        REQUIRED_PERMISSIONS!!, REQUEST_CODE_PERMISSIONS
      )
    } else {
      startHomeFragment()
    }
  }

  private fun startHomeFragment() {
    participantId = ""
    fragmentManager = supportFragmentManager
    val homeFragment: Fragment = HomeFragment()
    changeFragment(homeFragment)
  }

  private fun hasPermissions(context: Context?): Boolean {
    if (context != null && REQUIRED_PERMISSIONS != null) {
      for (permission in REQUIRED_PERMISSIONS) {
        if (ActivityCompat.checkSelfPermission(context, permission)
          != PackageManager.PERMISSION_GRANTED
        ) {
          return false
        }
      }
    }
    return true
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (REQUEST_CODE_PERMISSIONS == requestCode) {
      if (grantResults.size < REQUIRED_PERMISSIONS!!.size || grantResults[0] != PackageManager.PERMISSION_GRANTED || grantResults[1] != PackageManager.PERMISSION_GRANTED) {
        if (permissionsRequestCount < MAX_PERMISSIONS_REQUESTS) {
          // Retry getting permissions.
          permissionsRequestCount++
          ActivityCompat.requestPermissions(
            this,
            REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
          )
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

  fun changeFragment(fragment: Fragment?) {
    fragmentManager.beginTransaction().replace(R.id.flContainer, fragment).commit()
  }

  fun createFile(internalName: String, fileExtension: String): File {

    // Data location is '/Download/StarterCamera/<participantId>/'
    val fileName = ("StarterCamera/"
      + participantId
      + '/'
      + participantId
      + "_"
      + internalName
      + "_"
      + System.currentTimeMillis()
      + "_"
      + APP_VERSION
      + fileExtension)
    val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val file = File(path, fileName)
    // Note that mkdirs will create all missing parent dirs, including the
    // participantId parent dir for any new participant if it doesn't exist yet.
    file.parentFile!!.mkdirs()
    return file
  }

  companion object {
    private val TAG = MainActivity::class.java.simpleName
    private const val MAX_PERMISSIONS_REQUESTS = 10
    private const val REQUEST_CODE_PERMISSIONS = 100
    private val REQUIRED_PERMISSIONS: Array<String>? = arrayOf(
      Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE
    )

    // The following version constant should be updated whenever we ship a new version of the app.
    // The version format is composed of two parts vXsY, which stands for version X subversion Y.
    // We do not use "." in the version string to avoid confusion with file suffixes.
    private const val APP_VERSION = "v2s1"
  }
}
