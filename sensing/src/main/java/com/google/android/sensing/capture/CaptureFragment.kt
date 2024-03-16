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
import com.google.android.sensing.model.CaptureType
import com.google.android.sensing.model.InternalSensorType
import com.google.android.sensing.model.ProcessedInfo
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

/**
 * Fragment that deals with all sensors: For now only camera with capture types being
 * [CaptureType.VIDEO_PPG] and [CaptureType.IMAGE]. The UI is programmatically inflated based on the
 * captureType given. Reason why all [CaptureType]s are managed by this fragment is for a given
 * capture type, multiple sensors may be required. Business logic like subscribing, emitting flows
 * of [SensorCaptureResult], timers, livedata handling are done in [CaptureViewModel]. TODO: This is
 * too customised and we will need to make it configurable. Configurability options:-
 * 1. CaptureRequestOptions could be a part of [captureInfo.captureSettings] instead of being
 * hardcoded in the viewModel
 * 2. WriteJpegFutureSubscriber could be any generic subscriber
 * 3. Video timer should not be hardcoded
 * 4. Other camera settings like DEFAULT_BACK_CAMERA, TORCH, LOCK_AFTER_MS, etc.
 * 5. TSVWriter can be configurable
 * 6. File suppliers should be more generic ==>> DONE
 */
@SuppressLint("UnsafeOptInUsageError")
class CaptureFragment : Fragment() {

  private val captureViewModel by viewModels<CaptureViewModel>()
  private var captureInfo: CaptureInfo? = null

  private lateinit var sensorManager: SensorManager

  private var preview: Preview? = null
  private lateinit var previewView: PreviewView
  private lateinit var recordFab: FloatingActionButton
  private lateinit var toggleFlashFab: FloatingActionButton
  private lateinit var recordTimer: TextView
  private lateinit var ppgInstruction: TextView
  private lateinit var ppgProgress: CircularProgressIndicator
  private lateinit var btnTakePhoto: AppCompatImageView

  /**
   * A setter function: All details about the capture. Not passing via arguments as it requires API
   * >= 33.
   *
   * If [captureInfo.recapture] is true then [captureInfo.captureId] must not be null
   */
  fun setCaptureInfo(captureInfo: CaptureInfo) {
    if (captureInfo.captureId == null) {
      captureInfo.captureId = UUID.randomUUID().toString()
    }
    this.captureInfo = captureInfo
  }

  @SuppressLint("UseRequireInsteadOfGet")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    captureViewModel.captureInfo = captureInfo!!
    sensorManager = SensorManager.getInstance(requireContext())
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View? {
    (requireActivity() as AppCompatActivity).supportActionBar?.hide()
    /** For a different [CaptureType] sensors may be initialized differently. */
    preview = Preview.Builder().build()
    prepareCapture(captureViewModel.captureInfo.captureType)
    setupObservers()
    val layout =
      when (captureViewModel.captureInfo.captureType) {
        CaptureType.VIDEO_PPG -> R.layout.fragment_video_ppg
        CaptureType.IMAGE -> R.layout.fragment_image
      }
    return inflater.inflate(layout, container, false)
  }

  private fun prepareCapture(captureType: CaptureType) {
    lifecycleScope.launch {
      sensorManager.init(
        InternalSensorType.CAMERA,
        requireContext(),
        viewLifecycleOwner,
        CameraInitConfig(
          cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
          useCases = listOf(preview!!)
        )
      )
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    with((sensorManager.getSensor(InternalSensorType.CAMERA) as Camera).cameraControl) {
      this.enableTorch(true)
      Camera2CameraControl.from(this).captureRequestOptions =
        captureViewModel.getCaptureRequestOptions(false)
    }
    when (captureViewModel.captureInfo.captureType) {
      CaptureType.VIDEO_PPG -> {
        previewView = view.findViewById(R.id.preview_view)
        recordFab = view.findViewById(R.id.record_fab)
        toggleFlashFab = view.findViewById(R.id.toggle_flash_fab)
        recordTimer = view.findViewById(R.id.record_timer)
        ppgInstruction = view.findViewById(R.id.ppg_instruction)
        ppgProgress = view.findViewById(R.id.ppg_progress)
        ppgProgress.max = captureViewModel.captureInfo.captureSettings.ppgTimer
        ppgProgress.progress = 0
        // Start the camera and preview
        preview!!.setSurfaceProvider(previewView.surfaceProvider)

        recordTimer.text = "00 : ${captureViewModel.captureInfo.captureSettings.ppgTimer}"
        recordFab.setOnClickListener {
          lifecycleScope.launch {
            if (sensorManager.isStarted(InternalSensorType.CAMERA)) {
              sensorManager.stop(InternalSensorType.CAMERA)
            } else {
              captureViewModel.startTimer()
              val captureRequest =
                with(captureViewModel.captureInfo) {
                  CameraCaptureRequest.ImageStreamRequest(
                    externalIdentifier = externalIdentifier,
                    outputFolder = captureFolder,
                    outputTitle = captureSettings.captureTitle,
                    bufferCapacity = Int.MAX_VALUE
                  )
                }
              sensorManager.start(InternalSensorType.CAMERA, captureRequest)
            }
          }
        }
      }
      CaptureType.IMAGE -> {
        previewView = view.findViewById(R.id.preview_view)
        toggleFlashFab = view.findViewById(R.id.toggle_flash_fab)
        btnTakePhoto = view.findViewById(R.id.btn_take_photo)
        preview!!.setSurfaceProvider(previewView.surfaceProvider)
        btnTakePhoto.setOnClickListener {
          lifecycleScope.launch {
            with(captureViewModel.captureInfo) {
              sensorManager.start(
                InternalSensorType.CAMERA,
                CameraCaptureRequest.ImageRequest(
                  externalIdentifier = externalIdentifier,
                  outputFolder = captureFolder,
                  outputTitle = captureSettings.captureTitle,
                )
              )
            }
          }
        }
      }
    }
    toggleFlashFab.setOnClickListener {
      toggleFlashWithView(
        sensorManager.getSensor(InternalSensorType.CAMERA)!! as Camera,
        toggleFlashFab,
        requireContext()
      )
    }
  }

  private fun setupObservers() {
    sensorManager.registerListener(
      sensorType = InternalSensorType.CAMERA,
      listener =
        object : SensorManager.AppDataCaptureListener {
          override fun onStart(captureInfo: CaptureInfo) {
            when (captureViewModel.captureInfo.captureType) {
              CaptureType.VIDEO_PPG -> {
                ppgInstruction.text = resources.getString(R.string.ppg_instruction_recording)
                recordFab.setImageResource(R.drawable.videocam_off)
                recordFab.imageTintList =
                  ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.colorPrimary)
                  )
              }
              CaptureType.IMAGE -> {
                // No need to do anything here as Image capturing will end in a moment
              }
            }
          }

          override fun onStopped(captureInfo: CaptureInfo) {
            captureViewModel.endTimer()
            val toastText =
              when (captureViewModel.captureInfo.captureType) {
                CaptureType.VIDEO_PPG -> "Video Saved"
                CaptureType.IMAGE -> "Image Saved"
              }
            Toast.makeText(requireContext(), toastText, Toast.LENGTH_SHORT).show()
          }

          override fun onCancelled(captureInfo: CaptureInfo?) {
            TODO("Not yet implemented")
          }

          override fun onRecordSaved(captureInfo: CaptureInfo) {
            setFragmentResult(TAG, bundleOf(CAPTURED to true, CAPTURED_ID to captureInfo.captureId))
          }

          override fun onPostProcessed(processedInfo: ProcessedInfo) {
            TODO("Not yet implemented")
          }

          override fun onError(exception: Exception, captureInfo: CaptureInfo?) {
            captureViewModel.endTimer()
            val toastText =
              when (captureViewModel.captureInfo.captureType) {
                CaptureType.VIDEO_PPG -> "Failed to save Video"
                CaptureType.IMAGE -> "Failed to save Image"
              }
            Toast.makeText(requireContext(), toastText, Toast.LENGTH_SHORT).show()
          }
        }
    )

    if (captureViewModel.captureInfo.captureType == CaptureType.VIDEO_PPG) {
      captureViewModel.isPhoneSafeToUse.observe(viewLifecycleOwner) {
        if (!it) {
          showOverheatDialog()
        } else {
          val timer = Timer()
          timer.schedule(
            object : TimerTask() {
              override fun run() {
                lockExposure()
              }
            },
            LOCK_AFTER_MS.toLong()
          )
        }
      }
      captureViewModel.timerLiveData.observe(viewLifecycleOwner) {
        val strDuration =
          String.format(
            Locale.ENGLISH,
            "%02d : %02d",
            0L /* minutes */,
            TimeUnit.MILLISECONDS.toSeconds(it)
          )
        recordTimer.text = strDuration
        ppgProgress.progress =
          captureViewModel.captureInfo.captureSettings.ppgTimer -
            TimeUnit.MILLISECONDS.toSeconds(it).toInt()
      }

      captureViewModel.automaticallyStopCapturing.observe(viewLifecycleOwner) {
        if (it) {
          lifecycleScope.launch { sensorManager.stop(InternalSensorType.CAMERA) }
        }
      }
    }
  }

  // Safe to ignore CameraControl futures
  private fun lockExposure() {
    Camera2CameraControl.from(
        (sensorManager.getSensor(InternalSensorType.CAMERA) as Camera).cameraControl
      )
      .captureRequestOptions = captureViewModel.getCaptureRequestOptions(true)
  }

  private fun toggleFlashWithView(
    c: Camera,
    toggleFlashFab: FloatingActionButton,
    context: Context
  ) {
    if (c.cameraInfo.torchState.value == TorchState.ON) {
      // Turn off flash
      c.cameraControl.enableTorch(false)
      toggleFlashFab.setImageResource(R.drawable.flashlight_off)
      toggleFlashFab.backgroundTintList =
        ColorStateList.valueOf(ContextCompat.getColor(context, android.R.color.black))
    } else {
      // Turn on flash
      c.cameraControl.enableTorch(true)
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
    const val LOCK_AFTER_MS = 1000
    const val CAPTURE_FRAGMENT_TAG = "capture_fragment_tag"
    const val CAPTURED = "captured"
    const val CAPTURED_ID = "captured-id"
    const val TAG = "CAPTURE_FRAGMENT"
  }
}
