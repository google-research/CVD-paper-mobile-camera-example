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

package com.google.android.sensing.capture.sample

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.sensing.R
import com.google.android.sensing.SensorManager
import com.google.android.sensing.capture.sensors.CameraCaptureRequest
import com.google.android.sensing.capture.sensors.CameraInitConfig
import com.google.android.sensing.model.CaptureInfo
import com.google.android.sensing.model.InternalSensorType
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Reference fragment for capturing via [SensorManager] for SensorType [InternalSensorType.CAMERA].
 * This fragment is a sample intended to accompany an upcoming research publication on models
 * trained on mobile-sensor data to evaluate CVD risk. Feel free to use this, however you may need
 * to modify this to tailor your needs.
 */
@SuppressLint("UnsafeOptInUsageError")
class CameraCaptureFragment : Fragment() {

  private val captureViewModel by viewModels<CaptureViewModel>()
  private lateinit var captureRequest: CameraCaptureRequest

  private lateinit var sensorManager: SensorManager

  private lateinit var preview: Preview
  private lateinit var previewView: PreviewView
  private lateinit var recordFab: FloatingActionButton
  private lateinit var toggleFlashFab: FloatingActionButton
  private lateinit var timerTV: TextView
  private lateinit var ppgInstruction: TextView
  private lateinit var timerProgress: CircularProgressIndicator
  private lateinit var takePhotoButton: AppCompatImageView

  /**
   * A setter function: All details about the capture. Not passing via arguments as it requires API
   * >= 33.
   */
  fun setCaptureRequest(captureRequest: CameraCaptureRequest) {
    this.captureRequest = captureRequest
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    sensorManager = SensorManager.getInstance(requireContext())
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View? {
    (requireActivity() as AppCompatActivity).supportActionBar?.hide()
    preview = Preview.Builder().build()
    setupSensorManagerForCapturing()
    setupSampleObservers()
    val layout =
      when (captureRequest) {
        is CameraCaptureRequest.ImageStreamRequest -> R.layout.fragment_image_stream
        is CameraCaptureRequest.ImageRequest -> R.layout.fragment_image
        is CameraCaptureRequest.VideoRequest -> TODO()
      }
    return inflater.inflate(layout, container, false)
  }

  private fun setupSensorManagerForCapturing() {
    lifecycleScope.launch {
      sensorManager.init(
        InternalSensorType.CAMERA,
        requireContext(),
        viewLifecycleOwner,
        CameraInitConfig(
          cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
          useCases = listOf(preview)
        )
      )
      sensorManager.registerListener(
        sensorType = InternalSensorType.CAMERA,
        listener =
          object : SensorManager.AppDataCaptureListener {
            override fun onStart(captureInfo: CaptureInfo) {
              handleOnStart(captureInfo)
            }

            override fun onComplete(captureInfo: CaptureInfo) {
              handleOnComplete(captureInfo)
            }

            override fun onError(exception: Exception, captureInfo: CaptureInfo?) {
              handleOnError(exception, captureInfo)
            }
          }
      )
    }
  }

  private fun handleOnStart(captureInfo: CaptureInfo) {
    when (captureRequest) {
      is CameraCaptureRequest.ImageStreamRequest -> {
        ppgInstruction.text = resources.getString(R.string.ppg_instruction_recording)
        recordFab.setImageResource(R.drawable.videocam_off)
        recordFab.imageTintList =
          ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.colorPrimary))
      }
      is CameraCaptureRequest.ImageRequest -> {
        // No need to do anything here as Image capturing will end in a moment
      }
      is CameraCaptureRequest.VideoRequest -> TODO()
    }
  }

  private fun handleOnComplete(captureInfo: CaptureInfo) {
    val toastText =
      when (captureRequest) {
        is CameraCaptureRequest.ImageStreamRequest -> {
          captureViewModel.endTimer()
          "Image Stream Saved"
        }
        is CameraCaptureRequest.ImageRequest -> "Image Saved"
        is CameraCaptureRequest.VideoRequest -> TODO()
      }
    Toast.makeText(requireContext(), toastText, Toast.LENGTH_SHORT).show()
    setFragmentResult(TAG, bundleOf(CAPTURED to true, CAPTURED_ID to captureInfo.captureId))
  }

  private fun handleOnError(exception: Exception, captureInfo: CaptureInfo?) {
    captureViewModel.endTimer()
    val toastText =
      when (captureRequest) {
        is CameraCaptureRequest.ImageStreamRequest -> "Failed to save image stream"
        is CameraCaptureRequest.ImageRequest -> "Failed to save Image"
        is CameraCaptureRequest.VideoRequest -> TODO()
      }
    Toast.makeText(requireContext(), toastText, Toast.LENGTH_SHORT).show()
    Timber.w("Capture (captureId = ${captureInfo?.captureId}) failed with error $exception")
  }

  private fun setupSampleObservers() {
    /** Sample fine-grained control over the underlying Sensor. */
    if (captureRequest is CameraCaptureRequest.ImageStreamRequest) {
      captureViewModel.isPhoneSafeToUse.observe(viewLifecycleOwner) {
        if (!it) {
          showOverheatDialog()
        } else {
          /** Locking the exposure after [LOCK_AFTER_MILLISECOND] milliseconds. */
          val timer = Timer()
          timer.schedule(
            object : TimerTask() {
              override fun run() {
                updateCameraCaptureRequestOptions(true)
              }
            },
            LOCK_AFTER_MILLISECOND.toLong()
          )
        }
      }
      /** Observe live timer that runs for [IMAGE_STREAM_TIMER_SECONDS] seconds. */
      captureViewModel.timerLiveData.observe(viewLifecycleOwner) {
        val strDuration =
          String.format(
            Locale.ENGLISH,
            "%02d : %02d",
            0L /* minutes */,
            TimeUnit.MILLISECONDS.toSeconds(it)
          )
        timerTV.text = strDuration
        timerProgress.progress =
          IMAGE_STREAM_TIMER_SECONDS - TimeUnit.MILLISECONDS.toSeconds(it).toInt()
      }

      /** Stop capturing once the timer stops. */
      captureViewModel.automaticallyStopCapturing.observe(viewLifecycleOwner) {
        if (it) {
          lifecycleScope.launch { sensorManager.stop(InternalSensorType.CAMERA) }
        }
      }
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    updateCameraCaptureRequestOptions(false)
    when (captureRequest) {
      is CameraCaptureRequest.ImageStreamRequest -> {
        previewView = view.findViewById(R.id.preview_view)
        recordFab = view.findViewById(R.id.record_fab)
        toggleFlashFab = view.findViewById(R.id.toggle_flash_fab)
        timerTV = view.findViewById(R.id.timer_text_view)
        ppgInstruction = view.findViewById(R.id.camera_request_instruction)
        timerProgress = view.findViewById(R.id.timer_progress)
        timerProgress.max = IMAGE_STREAM_TIMER_SECONDS
        timerProgress.progress = 0
        // Start the camera and preview
        preview.setSurfaceProvider(previewView.surfaceProvider)

        timerTV.text = "00 : $IMAGE_STREAM_TIMER_SECONDS"
        recordFab.setOnClickListener {
          lifecycleScope.launch {
            if (sensorManager.isStarted(InternalSensorType.CAMERA)) {
              sensorManager.stop(InternalSensorType.CAMERA)
            } else {
              captureViewModel.startTimer()
              sensorManager.start(InternalSensorType.CAMERA, captureRequest)
            }
          }
        }
      }
      is CameraCaptureRequest.ImageRequest -> {
        previewView = view.findViewById(R.id.preview_view)
        toggleFlashFab = view.findViewById(R.id.toggle_flash_fab)
        takePhotoButton = view.findViewById(R.id.take_photo_buttton)
        preview.setSurfaceProvider(previewView.surfaceProvider)
        takePhotoButton.setOnClickListener {
          lifecycleScope.launch { sensorManager.start(InternalSensorType.CAMERA, captureRequest) }
        }
      }
      is CameraCaptureRequest.VideoRequest -> TODO()
    }
    toggleFlashFab.setOnClickListener {
      toggleFlashWithView(getUnderlyingCamera(), toggleFlashFab, requireContext())
    }
    toggleFlashFab.performClick()
  }

  private fun updateCameraCaptureRequestOptions(lockExposure: Boolean) {
    Camera2CameraControl.from(getUnderlyingCamera().cameraControl).captureRequestOptions =
      captureViewModel.getCaptureRequestOptions(lockExposure)
  }

  private fun getUnderlyingCamera() = sensorManager.getSensor(InternalSensorType.CAMERA)!! as Camera

  private fun toggleFlashWithView(
    camera: Camera,
    toggleFlashFab: FloatingActionButton,
    context: Context
  ) {
    if (camera.cameraInfo.torchState.value == TorchState.ON) {
      // Turn off flash
      camera.cameraControl.enableTorch(false)
      toggleFlashFab.setImageResource(R.drawable.flashlight_off)
      toggleFlashFab.backgroundTintList =
        ColorStateList.valueOf(ContextCompat.getColor(context, android.R.color.black))
    } else {
      // Turn on flash
      camera.cameraControl.enableTorch(true)
      toggleFlashFab.setImageResource(R.drawable.flashlight_on)
      toggleFlashFab.backgroundTintList =
        ColorStateList.valueOf(ContextCompat.getColor(context, R.color.colorPrimary))
    }
  }

  private fun showOverheatDialog() {
    val builder = AlertDialog.Builder(requireActivity())
    builder
      .setTitle(R.string.overheat_dialog_title)
      .setMessage(R.string.overheat_dialog_message)
      .setPositiveButton(R.string.overheat_ok_to_proceed) { dialog, which ->
        captureViewModel.isPhoneSafeToUse.postValue(true)
      }
      .setCancelable(false)
    val dialog = builder.create()
    dialog.show()
  }

  override fun onPause() {
    super.onPause()
    (requireActivity() as AppCompatActivity).supportActionBar?.show()
  }

  companion object {
    const val LOCK_AFTER_MILLISECOND = 1000
    const val IMAGE_STREAM_TIMER_SECONDS = 30
    const val CAPTURED = "captured"
    const val CAPTURED_ID = "captured-id"
    const val TAG = "CAPTURE_FRAGMENT"
  }
}
