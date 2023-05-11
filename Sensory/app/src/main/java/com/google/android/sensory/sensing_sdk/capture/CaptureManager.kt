package com.google.android.sensory.sensing_sdk.capture

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.sensory.fitbit_sensing_library.StreamToTsvSubscriber
import com.google.android.sensory.fitbit_sensing_library.WriteJpegFutureSubscriber
import com.google.android.sensory.sensing_sdk.capture.model.CaptureSettings
import com.google.android.sensory.sensing_sdk.model.CaptureInfo
import com.google.android.sensory.sensing_sdk.model.CaptureType
import com.google.android.sensory.sensing_sdk.model.SensorType
import java.io.File

class CaptureManager(private val context: AppCompatActivity, private val captureInfo: CaptureInfo) {
  /** DialogFragment().showNow commits the transaction synchronously*/
  private var recordedFrames = 0
  private val captured = MutableLiveData<Boolean>()
  fun capture(captureSettings: CaptureSettings) {
    when(captureInfo.captureType){
      CaptureType.VIDEO_PPG -> {
        val jpegFutureSubscriber = WriteJpegFutureSubscriber()
        val tsvSubscriber = StreamToTsvSubscriber()
        val sensorDataMap = mapOf(SensorType.CAMERA to (jpegFutureSubscriber to tsvSubscriber))
        StarterCameraFragment(captureSettings, sensorDataMap)
          .show(context.supportFragmentManager, "StarterCameraFragment")
      }
      CaptureType.IMAGE -> {
        PhotoCaptureFragment(captureSettings, getJpegFile(), getTsvFile(), futurelistener)
          .show(context.supportFragmentManager, "PhotoCaptureFragment")

      }
    }
  }

  private fun getJpegFile(): File {
    val file = File("${captureInfo.captureId}/PPG_$recordedFrames.jpg")
    recordedFrames++
    return file
  }

  private fun getTsvFile(): File? {
    return File("${captureInfo.captureId}/PPG_$recordedFrames.tsv")
  }

  companion object {
    const val CAPTURE_COMPLETE = "capture_completed"
  }
}