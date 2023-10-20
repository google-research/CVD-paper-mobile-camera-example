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
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.fitbit.research.sensing.common.libraries.camera.Camera2InteropSensor
import com.google.android.fitbit.research.sensing.common.libraries.camera.CameraXSensorV2
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.sensing.R
import com.google.android.sensing.model.CaptureInfo
import com.google.android.sensing.model.CaptureType
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
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
  private lateinit var sensorCaptureResultCollector: suspend ((Flow<SensorCaptureResult>) -> Unit)
  private var captureInfo: CaptureInfo? = null

  private var camera: Camera2InteropSensor? = null

  private var preview: Preview? = null
  private lateinit var previewView: PreviewView
  private lateinit var recordFab: FloatingActionButton
  private lateinit var toggleFlashFab: FloatingActionButton
  private lateinit var recordTimer: TextView
  private lateinit var btnTakePhoto: Button

  /**
   * A setter function: Callback defined by the application developers that collects
   * [SensorCaptureResult] emitted from [CaptureViewModel] and
   * [SensingEngine.onCaptureCompleteCallback].
   */
  fun setSensorCaptureResultCollector(
    sensorCaptureResultCollector: suspend ((Flow<SensorCaptureResult>) -> Unit)
  ) {
    this.sensorCaptureResultCollector = sensorCaptureResultCollector
  }

  /**
   * A setter function: All details about the capture. Not passing via arguments as it requires API
   * >= 33.
   */
  fun setCaptureInfo(captureInfo: CaptureInfo) {
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
    /** *** To fit full screen */
    requireActivity().window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
    (requireActivity() as AppCompatActivity).supportActionBar?.hide()
    /** *** To fit full screen */
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
        recordTimer.text = "00 : 30"
        recordFab.setOnClickListener { captureViewModel.processRecord(camera!!) }
        toggleFlashFab.setOnClickListener {
          CaptureUtil.toggleFlashWithView(camera!!, toggleFlashFab)
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
          CaptureUtil.toggleFlashWithView(camera!!, toggleFlashFab)
        }
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
        captureViewModel.timerLiveData.distinctUntilChanged().observe(viewLifecycleOwner) {
          if (it == null) return@observe
          val strDuration =
            String.format(
              Locale.ENGLISH,
              "%02d : %02d",
              0L /* minutes */,
              TimeUnit.MILLISECONDS.toSeconds(it)
            )
          recordTimer.text = strDuration
        }
      }
      captureViewModel.captured.observe(viewLifecycleOwner) {
        if (it == null) return@observe
        if (!it) {
          val toastText =
            when (captureViewModel.captureInfo.captureType) {
              CaptureType.VIDEO_PPG -> "Failed to save Video"
              CaptureType.IMAGE -> "Failed to save Image"
            }
          Toast.makeText(requireContext(), toastText, Toast.LENGTH_SHORT).show()
          return@observe
        }
        val toastText =
          when (captureViewModel.captureInfo.captureType) {
            CaptureType.VIDEO_PPG -> "Video Saved"
            CaptureType.IMAGE -> "Image Saved"
          }
        Toast.makeText(requireContext(), toastText, Toast.LENGTH_SHORT).show()
        stopRecording()
      }
      lifecycleScope.launch {
        captureViewModel.captureResultFlow.collect {
          if (it is SensorCaptureResult.Started) {
            recordFab.setImageResource(R.drawable.videocam_off)
          }
          if (it is SensorCaptureResult.CaptureComplete) {
            finishCapturing()
          }
        }
      }
    }
  }

  private fun stopRecording() {
    finishCapturing()
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
    captureViewModel.captureComplete()
    setFragmentResult(CAPTURE_FRAGMENT_TAG, bundleOf(CAPTURED to true))
    requireActivity().supportFragmentManager.popBackStack()
  }

  override fun onDetach() {
    if (captureViewModel.captured.value == null || !captureViewModel.captured.value!!) {
      setFragmentResult(CAPTURE_FRAGMENT_TAG, bundleOf(CAPTURED to false))
    }
    /** *** To remove full screen */
    requireActivity().window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    (requireActivity() as AppCompatActivity).supportActionBar?.show()
    /** *** To remove full screen */
    super.onDetach()
  }

  companion object {
    const val LOCK_AFTER_MS = 1000
    const val CAPTURE_FRAGMENT_TAG = "capture_fragment_tag"
    const val CAPTURED = "captured"
    const val TAG = "CAPTURE_FRAGMENT"
  }
}
