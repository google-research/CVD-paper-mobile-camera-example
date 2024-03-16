/*
 * Copyright 2024 Google LLC
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

package com.google.android.sensing.capture.sensors

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.media.MediaRecorder
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.fitbit.research.sensing.common.libraries.camera.storage.ImageEncoders
import com.google.android.sensing.SensorFactory
import com.google.android.sensing.capture.CaptureMode
import com.google.android.sensing.capture.InitConfig
import com.google.android.sensing.capture.SharedCloseable
import com.google.android.sensing.capture.use
import com.google.android.sensing.model.InternalSensorType
import java.io.File
import java.io.FileWriter
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.zip
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber

data class CameraData(
  val sharedImageProxy: SharedCloseable<ImageProxy>,
  val captureResult: CaptureResult
)

internal class CameraSensor(
  context: Context,
  private val lifecycleOwner: LifecycleOwner,
  private val initConfig: CameraInitConfig,
) : Sensor {

  private val internalStorage = context.filesDir
  private val internalCameraInitJob: Job

  private lateinit var processCameraProvider: ProcessCameraProvider
  /**
   * Create an internal [ImageAnalysis] [UseCase] that constructs [_imageFlow] and [_metaDataFlow].
   */
  private lateinit var internalImageAnalysis: ImageAnalysis

  init {
    internalCameraInitJob =
      lifecycleOwner.lifecycleScope.launch {
        /**
         * This launches a new coroutine in Main Thread Pool to get a [ProcessCameraProvider]
         * instance asynchronously.
         */
        var resumeCount = 0
        try {
          suspendCancellableCoroutine<ProcessCameraProvider> { continuation ->
              if (resumeCount > 5) {
                CancellationException("Failed to get CameraProvider.").let {
                  continuation.resumeWithException(it)
                  cancel(it)
                }
              }
              val cameraFutureProvider = ProcessCameraProvider.getInstance(context)
              try {
                continuation.resume(cameraFutureProvider.get())
              } catch (e: ExecutionException) {
                resumeCount++
                continuation.resumeWithException(Exception("Failed to get CameraProvider."))
              }
            }
            .let {
              processCameraProvider = it
              buildData()
              cancel()
            }
        } catch (e: Exception) {
          internalListener.onError(InternalSensorType.CAMERA, e)
        }
      }
  }

  private lateinit var currentCaptureRequest: CameraCaptureRequest
  private lateinit var internalListener: Sensor.InternalSensorListener

  private lateinit var camera: Camera

  /**
   * A [SharedFlow] and not a [StateFlow] because of 2 reasons:-
   * 1. Provides Subscription APIs: onSubscription, subscriptionCount
   * 2. Maintains a [SharedFlow.replayCache] for new subscribers
   *
   * Here replay = 1 is needed so that it can be played for the new subscriber to close the
   * ImageProxy instance and hence receive new ImageProxies.
   */
  private val _imageFlow = MutableSharedFlow<ImageProxy>(replay = 1)
  private val _metaDataFlow = MutableSharedFlow<CaptureResult>(replay = 1)

  /** This is constructed when [start] is invoked. */
  private lateinit var data: Flow<CameraData>

  private var isStarted = AtomicBoolean(false)
  private var dataCount = 0

  @SuppressLint("UnsafeOptInUsageError")
  private fun buildData() {
    /** Build [_metaDataFlow]. */
    val captureCallback =
      object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
          session: CameraCaptureSession,
          request: CaptureRequest,
          result: TotalCaptureResult
        ) {
          _metaDataFlow.tryEmit(result)
        }
      }
    // Register the callback for the given UseCase
    val internalImageAnalysisBuilder = ImageAnalysis.Builder()
    Camera2Interop.Extender(internalImageAnalysisBuilder).setSessionCaptureCallback(captureCallback)

    /** Build [_imageFlow]. */
    internalImageAnalysis = internalImageAnalysisBuilder.build()
    internalImageAnalysis.setAnalyzer(
      Executors.newSingleThreadExecutor {
        Thread(it).apply { name = CameraSensor::class.java.getName() + ".ImageAnalysisThread" }
      }
    ) { _imageFlow.tryEmit(it) }

    /** Get [Camera] instance responsible for interaction with the physical sensor. */
    camera =
      with(initConfig) {
        processCameraProvider.bindToLifecycle(
          lifecycleOwner,
          cameraSelector,
          *useCases.toTypedArray(),
          internalImageAnalysis
        )
      }
  }

  override suspend fun prepare(internalSensorListener: Sensor.InternalSensorListener) {
    if (isStarted()) {
      Timber.w("Call to #prepare the capture is redundant as Sensor is currently capturing.")
      return
    }
    /** Suspend until [Camera] is available. */
    if (internalCameraInitJob.isActive) {
      kotlin.runCatching { internalCameraInitJob.join() }.onFailure(Timber::w)
    }
    internalListener = internalSensorListener
  }

  override suspend fun start(captureRequest: com.google.android.sensing.capture.CaptureRequest) {
    if (captureRequest !is CameraCaptureRequest) {
      throw IllegalArgumentException(
        "Invalid Request. CameraSensor needs a CameraCaptureRequest. Given = ${captureRequest::class.java}"
      )
    }
    if (isStarted()) {
      throw IllegalStateException(
        "Call to #start capturing is redundant as Sensor is currently capturing."
      )
    }
    if (internalCameraInitJob.isActive || !::internalListener.isInitialized) {
      throw IllegalStateException(
        "CameraProvider not yet available OR internal listener not initialized. Call #prepare first."
      )
    }
    currentCaptureRequest = captureRequest
    File(internalStorage, currentCaptureRequest.outputFolder).mkdirs()
    data = _imageFlow.zip(_metaDataFlow) { a, b -> CameraData(SharedCloseable(a), b) }
    data
      .onStart {
        isStarted.set(true)
        dataCount = 0
        internalListener.onStarted(InternalSensorType.CAMERA)
      }
      .onEach {
        dataCount++
        internalListener.onData(InternalSensorType.CAMERA)
      }
      .onCompletion {
        /**
         * This completes gracefully when [getMaxDataCount] returns a finite value and there is no
         * manual stopping. For example in the case of capturing a snapshot image.
         */
        stop()
      }
      .buffer(capacity = currentCaptureRequest.bufferCapacity)
      .take(getMaxDataCount())
      .collect { saveData(it) }
  }

  override suspend fun stop() {
    internalStop()
    internalListener.onStopped(InternalSensorType.CAMERA)
  }

  private suspend fun internalStop() {
    if (isStarted()) {
      isStarted.set(false)
      internalImageAnalysis.clearAnalyzer()
    }
  }

  override suspend fun reset() {
    processCameraProvider.unbindAll()
  }

  override suspend fun pause() {
    TODO("Not yet implemented")
  }

  override suspend fun resume() {
    TODO("Not yet implemented")
  }

  override fun cancel() = runBlocking {
    internalStop()
    internalListener.onCancelled(InternalSensorType.CAMERA)
  }

  override fun getSensor() = camera

  override fun isStarted() = isStarted.get()

  override fun getCaptureMode() = CaptureMode.ACTIVE

  /** TODO support VIDEO Data. */
  private suspend fun saveData(cameraData: CameraData) {
    saveImage(cameraData.sharedImageProxy, getDataFile())
    saveMetadata(cameraData.captureResult, getMetadataFile())
  }

  @SuppressLint("UnsafeOptInUsageError")
  private suspend fun saveImage(sharedImage: SharedCloseable<ImageProxy>, outputFile: File) {
    // Convert Image to YuvImage immediately, then enqueue the YuvImage for writing.
    // This allows the Image to be released before the write completes, which unblocks the camera
    // and allows it to produce the next frame immediately.
    val yuvImage = sharedImage.use { ImageEncoders.toYuvImage(it.image) }
    withContext(Dispatchers.IO) {
      outputFile.outputStream().use {
        val success =
          yuvImage.compressToJpeg(
            Rect(0, 0, yuvImage.width, yuvImage.height),
            currentCaptureRequest.compressionQuality,
            it
          )
        if (!success) {
          throw Exception("Failed to compress YuvImage to JPEG")
        }
      }
    }
  }

  private suspend fun saveMetadata(captureResult: CaptureResult, outputFile: File) {
    withContext(Dispatchers.IO) {
      FileWriter(outputFile).use { fileWriter ->
        captureResult.keys.let {
          // Write header (key names)
          fileWriter.append(it.joinToString(separator = "\t") { it.name })
          fileWriter.append("\n")

          fileWriter.append(
            it.joinToString(separator = "\t") { key ->
              captureResult.get(key)?.toString() ?: "NULL"
            }
          )
          fileWriter.append("\n")
        }
      }
    }
  }

  private fun getMaxDataCount(): Int {
    return currentCaptureRequest.maxDataCount ?: Int.MAX_VALUE
  }

  /** Format = request.folder/<frameNumber>_<timestamp>.<request.imageFormat>. */
  fun getDataFile(): File {
    with(currentCaptureRequest) {
      val filename = "${dataCount}_${System.currentTimeMillis()}.$outputFormat"
      val filePath = "$outputFolder/$filename"
      return File(internalStorage, filePath)
    }
  }

  /** Format = request.folder/<frameNumber>_<timestamp>.tsv. */
  fun getMetadataFile(): File {
    with(currentCaptureRequest) {
      val filename = "${dataCount}_${System.currentTimeMillis()}.tsv"
      val filePath = "$outputFolder/$filename"
      return File(internalStorage, filePath)
    }
  }
}

data class CameraInitConfig(
  val cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
  val useCases: List<UseCase> = emptyList()
) : InitConfig(CaptureMode.ACTIVE)

sealed class CameraCaptureRequest(
  override val externalIdentifier: String,
  override val outputFolder: String,
  override val outputFormat: String,
  override val outputTitle: String,
  open val compressionQuality: Int = 100,
  open val bufferCapacity: Int = Int.MAX_VALUE,
  open val maxDataCount: Int? = null,
) :
  com.google.android.sensing.capture.CaptureRequest(
    externalIdentifier,
    outputFolder,
    outputFormat,
    outputTitle
  ) {

  data class ImageRequest(
    override val externalIdentifier: String,
    override val outputTitle: String,
    override val outputFolder: String,
    override val outputFormat: String = "jpeg",
    override val compressionQuality: Int = 100,
  ) :
    CameraCaptureRequest(
      externalIdentifier,
      outputFolder,
      outputFormat,
      outputTitle,
      compressionQuality,
      maxDataCount = 1
    )

  data class ImageStreamRequest(
    override val externalIdentifier: String,
    override val outputTitle: String,
    override val outputFolder: String,
    override val outputFormat: String = "jpeg",
    override val compressionQuality: Int = 100,
    override val bufferCapacity: Int,
    override val maxDataCount: Int? = null,
  ) :
    CameraCaptureRequest(
      externalIdentifier,
      outputFolder,
      outputFormat,
      outputTitle,
      compressionQuality,
      bufferCapacity,
      maxDataCount
    )

  // Not used currently
  data class VideoRequest(
    override val externalIdentifier: String,
    override val outputFolder: String,
    override val outputFormat: String = "video/mp4v-es",
    override val outputTitle: String,
    override val compressionQuality: Int = 100,
    override val bufferCapacity: Int,
    override val maxDataCount: Int? = null,
    val videoEncoder: Int? = MediaRecorder.VideoEncoder.DEFAULT,
    val audioEncoder: Int? = MediaRecorder.AudioEncoder.DEFAULT,
  ) :
    CameraCaptureRequest(
      externalIdentifier,
      outputFolder,
      outputFormat,
      outputTitle,
      compressionQuality,
      bufferCapacity,
      maxDataCount
    )
}

internal object CameraSensorFactor : SensorFactory {
  override fun create(context: Context, lifecycleOwner: LifecycleOwner, initConfig: InitConfig) =
    CameraSensor(context, lifecycleOwner, initConfig as CameraInitConfig)
}
