package com.google.android.sensory.sensing_sdk.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.google.android.sensory.R
import com.google.android.sensory.sensing_sdk.impl.SensingEngineImpl
import com.google.android.sensory.sensing_sdk.model.CaptureInfo
import com.google.android.sensory.sensing_sdk.model.CaptureType
import com.google.android.sensory.sensing_sdk.model.SensorType
import java.io.File
import java.io.FileOutputStream
import org.reactivestreams.Subscriber


typealias SensorDataMap = Map<SensorType, Pair<Subscriber<Any>, Subscriber<Any>>>
class CaptureManager(private val context: FragmentActivity) {

  private val TAG = "CaptureManager"
  private val CONTENT_VIEW_ID = 10101010
  private var recordedFrames = 0
  fun capture(captureInfo: CaptureInfo, onCaptureComplete: ((CaptureInfo) -> String)) {
    when (captureInfo.captureType) {
      CaptureType.VIDEO_PPG -> {
        // val jpegFutureSubscriber = WriteJpegFutureSubscriber()
        // val tsvSubscriber = StreamToTsvSubscriber()
        // val sensorDataMap = mapOf(SensorType.CAMERA to (jpegFutureSubscriber to tsvSubscriber))
        // StarterCameraFragment(captureSettings, sensorDataMap, onCaptureComplete)
        //   .show(context.supportFragmentManager, "StarterCameraFragment")
        // Assume this stores bunch of files in ..../Capture/<CaptureId>
        val resourceFolderRelativePath =
          SensingEngineImpl.resourceInfoFileUri(SensorType.CAMERA, captureInfo)
        // /data/data/<app>/app_data/Participants/<folderId>/<captureType>/<sensorType>
        Log.i(TAG, "capture: $resourceFolderRelativePath")

        for(i in 0..200){
          /** This is not right. We would want to store the data in internal storage, instead of external storage because of privacy concerns. */
          val resourceFilePath = "$resourceFolderRelativePath/ppg_$i.jpeg"
          val resourceFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), resourceFilePath)
          if(!resourceFile.parentFile?.exists()!!){
            val created = resourceFile.parentFile?.mkdirs()
            Log.i(TAG, "capture: resourceFolderFile created: $created")
          }
          val tempBitmap =
            BitmapFactory.decodeResource(context.resources, R.drawable.measure_ppg)
          FileOutputStream(resourceFile).use {
            tempBitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
          }
        }
        val captureId = onCaptureComplete(captureInfo)
        /** TO INTEGRATE WITH SDC*/
        // context.supportFragmentManager.setFragmentResult(
        //   CAPTURE_COMPLETE,
        //   bundleOf("RESOURCE_INFO")
        // )
        // val ft = context.supportFragmentManager.beginTransaction()
        // ft.add(CONTENT_VIEW_ID, instructionFragment).commit();
      }

      CaptureType.IMAGE -> {
        // PhotoCaptureFragment(captureSettings, getJpegFile(), getTsvFile(), futurelistener)
        //   .show(context.supportFragmentManager, "PhotoCaptureFragment")

      }
    }
    // fun getJpegFile(): File {
    //   val file = File("${captureInfo.folderId}/PPG_$recordedFrames.jpg")
    //   recordedFrames++
    //   return file
    // }
    // fun getTsvFile(): File {
    //   return File("${captureInfo.folderId}/PPG_$recordedFrames.tsv")
    // }
  }

  companion object {
    const val CAPTURE_COMPLETE = "capture_completed"

    fun sensorsInvolved(captureType: CaptureType): List<SensorType>{
      return when(captureType){
        CaptureType.VIDEO_PPG -> listOf(SensorType.CAMERA)
        CaptureType.IMAGE -> listOf(SensorType.CAMERA)
      }
    }
  }
}