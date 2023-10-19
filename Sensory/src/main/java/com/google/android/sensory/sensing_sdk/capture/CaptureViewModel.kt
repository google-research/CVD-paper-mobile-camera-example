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

package com.google.android.sensory.sensing_sdk.capture

import android.app.Application
import android.content.Context
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.params.ColorSpaceTransform
import android.hardware.camera2.params.RggbChannelVector
import android.os.CountDownTimer
import android.os.Environment
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.android.sensory.sensing_sdk.SensingEngineProvider
import com.google.android.sensory.sensing_sdk.model.CaptureInfo
import com.google.android.sensory.sensing_sdk.model.SensorType
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.fitbit.research.sensing.common.libraries.camera.Camera2InteropActions
import com.google.fitbit.research.sensing.common.libraries.camera.Camera2InteropSensor
import com.google.fitbit.research.sensing.common.libraries.camera.SharedImageProxy
import com.google.fitbit.research.sensing.common.libraries.camera.camera2.Camera2TsvWriters
import com.google.fitbit.research.sensing.common.libraries.camera.storage.WriteJpegFutureSubscriber
import com.google.fitbit.research.sensing.common.libraries.flow.FlowGate
import com.google.fitbit.research.sensing.common.libraries.storage.StreamToTsvSubscriber
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for CaptureFragment: Business logic like subscribing, emitting flows of
 * [SensorCaptureResult], timers, livedata handling. Maintains a flow of [SensorCaptureResult] which
 * is passed as a parameter to the [captureResultCollector] callback defined by the application
 * developers.
 */
@ExperimentalCamera2Interop
class CaptureViewModel(application: Application) : AndroidViewModel(application) {
  lateinit var captureInfo: CaptureInfo

  private val _captureResultFlow = MutableSharedFlow<SensorCaptureResult>()
  val captureResultFlow: Flow<SensorCaptureResult>
    get() = _captureResultFlow

  lateinit var recordingGate: FlowGate
  val isPhoneSafeToUse = MutableLiveData<Boolean>(false)
  private lateinit var countDownTimer: CountDownTimer
  val timerLiveData = MutableLiveData<Long?>(null)
  val captured = MutableLiveData<Boolean?>(null)

  fun setupCaptureResultFlow(
    captureInfo: CaptureInfo,
    captureResultCollector: suspend ((Flow<SensorCaptureResult>) -> Unit)
  ) {
    viewModelScope.launch {
      if (captureInfo.captureId != null) {
        // delete everything in folder associated with this captureId to re-capture
        val file =
          File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            captureInfo.captureFolder
          )
        file.deleteRecursively()
      } else {
        captureInfo.apply { captureId = UUID.randomUUID().toString() }
      }
    }
    this.captureInfo = captureInfo
    CoroutineScope(context = Dispatchers.IO).launch { captureResultCollector(captureResultFlow) }
  }
  fun processRecord(camera: Camera2InteropSensor) {
    if (this::recordingGate.isInitialized && recordingGate.isOpen) {
      recordingGate.completeAndClose()
      countDownTimer.cancel()
      viewModelScope.launch {
        _captureResultFlow.emit(SensorCaptureResult.CaptureComplete(captureInfo.captureId!!))
      }
      return
    }
    recordingGate = FlowGate.createClosed()
    if (!isPhoneSafeToUse.value!!) {
      isPhoneSafeToUse.postValue(false)
    }
    // Get stream of images while recording
    val recordingImages = recordingGate.passThrough(camera.dataPublisher())

    // Compatibility layer between camerax ImageProxy and camera2 Image
    SharedImageProxy.asImagePublisher(recordingImages) // Write from stream to disk as JPEGs
      .subscribe(
        WriteJpegFutureSubscriber.builder()
          .setFileSupplier { getCameraResourceFile() }
          .setTotalFrames(Long.MAX_VALUE)
          .build()
      )
    val captureResultStream = recordingGate.passThrough(camera.captureResultPublisher())
    val cameraMetadataSaver =
      StreamToTsvSubscriber.builder<CaptureResult>()
        .setTsvWriter(TSV_WRITER)
        .setSingleFile(getCameraMetadataFile())
        .build()
    captureResultStream.subscribe(cameraMetadataSaver)

    // Open the recording stream
    recordingGate.open()
    viewModelScope.launch {
      _captureResultFlow.emit(SensorCaptureResult.Started(captureInfo.captureId!!))
      // timer in a different coroutine
      startTimer()
    }
  }

  // Safe to ignore CameraControl futures
  fun capturePhoto(camera: Camera2InteropSensor, context: Context) {
    Futures.addCallback(
      Camera2InteropActions.captureSingleJpegWithMetadata(
        camera,
        { getCameraResourceFile() },
        { getCameraMetadataFile() },
        Executors.newSingleThreadExecutor()
      ),
      object : FutureCallback<Boolean?> {
        override fun onSuccess(success: Boolean?) {
          captured.postValue(true)
        }

        override fun onFailure(t: Throwable) {
          captured.postValue(false)
        }
      },
      ContextCompat.getMainExecutor(context)
    )
  }

  private fun getCameraResourceFile(): File {
    val folder = "${captureInfo.captureFolder}/${SensorType.CAMERA}"
    val filename =
      "Participant${captureInfo.participantId}_${captureInfo.captureSettings.captureTitle}_data_${System.currentTimeMillis()}_${captureInfo.captureSettings.titleMap[SensorType.CAMERA]}.${captureInfo.captureSettings.fileTypeMap[SensorType.CAMERA]}"
    val filePath = "$folder/$filename"
    val fileFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    return File(fileFolder, filePath)
  }

  private fun getCameraMetadataFile(): File {
    val folder = "${captureInfo.captureFolder}/${SensorType.CAMERA}"
    val filename =
      "Participant_${captureInfo.participantId}_${captureInfo.captureSettings.captureTitle}_metadata_${System.currentTimeMillis()}_${captureInfo.captureSettings.titleMap[SensorType.CAMERA]}.${captureInfo.captureSettings.metaDataTypeMap[SensorType.CAMERA]}"
    val filePath = "$folder/$filename"
    val fileFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    return File(fileFolder, filePath)
  }

  private suspend fun startTimer() {
    countDownTimer =
      object : CountDownTimer(30000, 1000) {
          override fun onTick(millisUntilFinished: Long) {
            timerLiveData.postValue(millisUntilFinished)
          }

          override fun onFinish() {
            if (recordingGate.isOpen) {
              viewModelScope.launch {
                _captureResultFlow.emit(
                  SensorCaptureResult.CaptureComplete(captureInfo.captureId!!)
                )
              }
            }
          }
        }
        .start()
  }

  fun getCaptureRequestOptions(lockExposure: Boolean): CaptureRequestOptions {
    // https://developer.android.com/reference/android/hardware/camera2/params/ColorSpaceTransform#ColorSpaceTransform(int[])
    // 3*3 identity matrix represented in numerator, denominator format
    val elements = intArrayOf(1, 1, 0, 1, 0, 1, 0, 1, 1, 1, 0, 1, 0, 1, 0, 1, 1, 1)
    // Set gains to give approximately similar gains for all color channels.
    val redGain = 0.5f
    val greenGain = 1.7f
    val blueGain = 3.0f
    // Disable gamma by setting the exponent to 1.
    val gamma = 1.0f
    val optionsBuilder =
      CaptureRequestOptions.Builder() // Disable white balancing so that we can control it manually.
        .setCaptureRequestOption(
          CaptureRequest.CONTROL_AWB_MODE,
          CaptureRequest.CONTROL_AWB_MODE_OFF
        ) // Set an identity correction matrix for color correction.
        .setCaptureRequestOption(
          CaptureRequest.COLOR_CORRECTION_MODE,
          CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX
        )
        .setCaptureRequestOption(
          CaptureRequest.COLOR_CORRECTION_TRANSFORM,
          ColorSpaceTransform(elements)
        ) // Set the individual channel gains.
        .setCaptureRequestOption(
          CaptureRequest.COLOR_CORRECTION_GAINS,
          RggbChannelVector(redGain, greenGain, greenGain, blueGain)
        ) // Set the manual gamma value.
        .setCaptureRequestOption(
          CaptureRequest.TONEMAP_MODE,
          CaptureRequest.TONEMAP_MODE_GAMMA_VALUE
        )
        .setCaptureRequestOption(CaptureRequest.TONEMAP_GAMMA, gamma)
        .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, lockExposure)
    return optionsBuilder.build()
  }

  /**
   * This invokes [SensingEngine.onCaptureCompleteCallback] which emits [SensorCaptureResult] upon
   * saving resources to database. Happens in [CoroutineScope] and not [viewModelScope] because the
   * fragment and its viewModel can get destroyed before resources are saved into the database.
   * Emitted [SensorCaptureResult] are collected here arnd re-emitted to the [_captureResultFlow]
   * which in turn is collected by the application developers via [captureResultCollector]
   */
  fun captureComplete() {
    CoroutineScope(context = Dispatchers.IO).launch {
      SensingEngineProvider.getOrCreateSensingEngine(getApplication())
        .onCaptureCompleteCallback(captureInfo)
        .collect { _captureResultFlow.emit(it) }
    }
  }

  companion object {
    private val TSV_WRITER =
      Camera2TsvWriters.captureResultBuilder()
        .addFrameNumberColumn()
        .addColumn(CaptureResult.SENSOR_TIMESTAMP)
        .addColumn(CaptureResult.SENSOR_FRAME_DURATION)
        .addColumn(CaptureResult.FLASH_MODE)
        .addColumn(CaptureResult.FLASH_STATE)
        .addColumn(CaptureResult.SENSOR_EXPOSURE_TIME)
        .addColumn(CaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST)
        .addColumn(CaptureResult.CONTROL_AE_MODE)
        .addColumn(CaptureResult.CONTROL_AE_LOCK)
        .addColumn(CaptureResult.CONTROL_AE_ANTIBANDING_MODE)
        .addColumn(CaptureResult.CONTROL_AE_STATE)
        .addColumn(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION)
        .addColumn(CaptureResult.SENSOR_SENSITIVITY)
        .addRangeColumn(CaptureResult.CONTROL_AE_TARGET_FPS_RANGE)
        .addColumn(CaptureResult.CONTROL_AWB_MODE)
        .addColumn(CaptureResult.CONTROL_AWB_STATE)
        .addColumn(CaptureResult.COLOR_CORRECTION_MODE)
        .addRggbChannelVectorColumn(CaptureResult.COLOR_CORRECTION_GAINS)
        .addColumn(CaptureResult.CONTROL_AF_MODE)
        .addColumn(CaptureResult.CONTROL_EFFECT_MODE)
        .addColumn(CaptureResult.NOISE_REDUCTION_MODE)
        .addColumn(CaptureResult.SHADING_MODE)
        .addColumn(CaptureResult.TONEMAP_MODE)
        .build()
  }
}
