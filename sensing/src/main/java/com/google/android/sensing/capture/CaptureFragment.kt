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

package com.google.android.sensing.capture

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.fitbit.research.sensing.common.libraries.camera.Camera2InteropSensor
import com.google.android.fitbit.research.sensing.common.libraries.camera.CameraXSensorV2
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.sensing.R
import com.google.android.sensing.model.CaptureInfo
import com.google.android.sensing.model.CaptureType
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow

/**
 * Fragment that displays screens to capture data from sensors involved in a captureType. The UI is
 * programmatically inflated based on the captureType given.
 *
 * Expects to set [CaptureInfo] and application callback to receive [SensorCaptureResult]s.
 *
 * Business logic like subscribing, emitting flows of [SensorCaptureResult], timers, livedata
 * handling are done in [CaptureViewModel].
 *
 * TODO: More configurability needs to be added:-
 * ```
 *      a. Camera settings like CaptureRequestOptions, torch, default_back_camera, etc.
 *      b. Accepting generic output subscribers for different sensor types (instead of just WriteJpegFutureSubscriber here)
 * ```
 */
@SuppressLint("UnsafeOptInUsageError")
class CaptureFragment : Fragment() {

  private val captureViewModel by viewModels<CaptureViewModel>()
  private lateinit var sensorCaptureResultCollector: suspend ((Flow<SensorCaptureResult>) -> Unit)
  private var captureInfo: CaptureInfo? = null

  private var camera: Camera2InteropSensor? = null

  private var preview: Preview? = null
  private lateinit var previewView: PreviewView
  private lateinit var recordFab: FloatingActionButton
  private lateinit var toggleFlashFab: FloatingActionButton
  private lateinit var recordTimer: TextView
  private lateinit var ppgInstruction: TextView
  private lateinit var ppgProgress: CircularProgressIndicator
  private lateinit var btnTakePhoto: AppCompatImageView

  /**
   * A setter function: Callback defined by the application developers that collects
   * [SensorCaptureResult] emitted from [CaptureViewModel] and
   * [SensingEngine.onCaptureCompleteCallback].
   */
  fun setSensorCaptureResultCollector(
    sensorCaptureResultCollector: suspend ((Flow<SensorCaptureResult>) -> Unit),
  ) {
    this.sensorCaptureResultCollector = sensorCaptureResultCollector
  }

  /**
   * A setter function: All details about the capture. Not passing via arguments as it requires API
   * >= 33.
   *
   * If [captureInfo.retake] is true then [captureInfo.captureId] must not be null
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
    captureViewModel.setupCaptureResultFlow(
      captureInfo = captureInfo!!,
      captureResultCollector = sensorCaptureResultCollector
    )
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View? {
    (requireActivity() as AppCompatActivity).supportActionBar?.hide()
    setupSensors()
    setupObservers()
    val layout =
      when (captureViewModel.captureInfo.captureType) {
        CaptureType.VIDEO_PPG -> R.layout.fragment_video_ppg
        CaptureType.IMAGE -> R.layout.fragment_image
      }
    return inflater.inflate(layout, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    when (captureViewModel.captureInfo.captureType) {
      CaptureType.VIDEO_PPG -> {
        previewView = view.findViewById(R.id.preview_view)
        recordFab = view.findViewById(R.id.record_fab)
        toggleFlashFab = view.findViewById(R.id.toggle_flash_fab)
        recordTimer = view.findViewById(R.id.record_timer)
        ppgInstruction = view.findViewById(R.id.ppg_instruction)
        ppgProgress = view.findViewById(R.id.ppg_progress)
        ppgProgress.max = captureViewModel.captureInfo.captureSettings!!.ppgTimer
        ppgProgress.progress = 0
        // Start the camera and preview
        preview!!.setSurfaceProvider(previewView.surfaceProvider)
        camera!!
          .lifecycle.addObserver(
            object : DefaultLifecycleObserver {
              override fun onCreate(owner: LifecycleOwner) {
                camera!!.cameraXSensor.cameraControl!!.enableTorch(true)
                camera!!.camera2Control!!.captureRequestOptions =
                  captureViewModel.getCaptureRequestOptions(false)
              }
            }
          )
        recordTimer.text = "00 : ${captureViewModel.captureInfo.captureSettings!!.ppgTimer}"
        recordFab.setOnClickListener { captureViewModel.processRecord(camera!!) }
        toggleFlashFab.setOnClickListener {
          CaptureUtil.toggleFlashWithView(camera!!, toggleFlashFab, requireContext())
        }
      }
      CaptureType.IMAGE -> {
        previewView = view.findViewById(R.id.preview_view)
        toggleFlashFab = view.findViewById(R.id.toggle_flash_fab)
        btnTakePhoto = view.findViewById(R.id.btn_take_photo)
        preview!!.setSurfaceProvider(previewView.surfaceProvider)
        camera!!
          .lifecycle.addObserver(
            object : DefaultLifecycleObserver {
              override fun onCreate(owner: LifecycleOwner) {
                camera!!.cameraXSensor.cameraControl!!.enableTorch(true)
                camera!!.camera2Control!!.captureRequestOptions =
                  CaptureRequestOptions.Builder().build()
              }
            }
          )
        btnTakePhoto.setOnClickListener {
          captureViewModel.capturePhoto(camera!!, requireContext())
        }
        toggleFlashFab.setOnClickListener {
          CaptureUtil.toggleFlashWithView(camera!!, toggleFlashFab, requireContext())
        }
      }
    }
  }

  private fun setupSensors() {
    /** For a different [CaptureType] sensors may be initialized differently. */
    when (captureViewModel.captureInfo.captureType) {
      CaptureType.VIDEO_PPG -> {
        preview = Preview.Builder().build()
        camera =
          Camera2InteropSensor.builder()
            .setContext(requireContext())
            .setBoundLifecycle(viewLifecycleOwner)
            .setCameraXSensorBuilder(
              CameraXSensorV2.builder()
                .setCameraSelector(CameraSelector.DEFAULT_BACK_CAMERA)
                .addUseCase(preview)
            )
            .build()
      }
      CaptureType.IMAGE -> {
        preview = Preview.Builder().build()
        camera =
          Camera2InteropSensor.builder()
            .setContext(requireContext())
            .setBoundLifecycle(viewLifecycleOwner)
            .setCameraXSensorBuilder(
              CameraXSensorV2.builder()
                .setCameraSelector(CameraSelector.DEFAULT_BACK_CAMERA)
                .addUseCase(preview)
            )
            .build()
      }
    }
  }

  private fun setupObservers() {
    lifecycleScope.launchWhenCreated {
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
            captureViewModel.captureInfo.captureSettings!!.ppgTimer -
              TimeUnit.MILLISECONDS.toSeconds(it).toInt()
        }
      }
      captureViewModel.captureResultLiveData.observe(viewLifecycleOwner) {
        when (it) {
          is SensorCaptureResult.Started -> {
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
          is SensorCaptureResult.CaptureComplete -> {
            val toastText =
              when (captureViewModel.captureInfo.captureType) {
                CaptureType.VIDEO_PPG -> "Video Saved"
                CaptureType.IMAGE -> "Image Saved"
              }
            Toast.makeText(requireContext(), toastText, Toast.LENGTH_SHORT).show()
            finishCapturing()
          }
          is SensorCaptureResult.Failed -> {
            val toastText =
              when (captureViewModel.captureInfo.captureType) {
                CaptureType.VIDEO_PPG -> "Failed to save Video"
                CaptureType.IMAGE -> "Failed to save Image"
              }
            Toast.makeText(requireContext(), toastText, Toast.LENGTH_SHORT).show()
          }
          else -> {}
        }
      }
    }
  }

  // Safe to ignore CameraControl futures
  private fun lockExposure() {
    camera!!.camera2Control!!.captureRequestOptions =
      captureViewModel.getCaptureRequestOptions(true)
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

  private fun finishCapturing() {
    captureViewModel.invokeCaptureCompleteCallback()
    setFragmentResult(TAG, bundleOf(CAPTURED to true))
  }

  override fun onPause() {
    super.onPause()
    captureViewModel.completeCapture()
    (requireActivity() as AppCompatActivity).supportActionBar?.show()
  }

  companion object {
    const val LOCK_AFTER_MS = 1000
    const val TAG = "CAPTURE_FRAGMENT"
    const val CAPTURED = "captured"
  }
}
