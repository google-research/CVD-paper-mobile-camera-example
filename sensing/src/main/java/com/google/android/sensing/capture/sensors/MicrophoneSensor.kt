package com.google.android.sensing.capture.sensors

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.lifecycle.LifecycleOwner
import com.google.android.sensing.SensorFactory
import com.google.android.sensing.capture.CaptureMode
import com.google.android.sensing.capture.CaptureRequest
import com.google.android.sensing.capture.InitConfig
import com.google.android.sensing.model.InternalSensorType
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber

@SuppressLint("MissingPermission")
internal class MicrophoneSensor(
    context: Context,
    private val lifecycleOwner: LifecycleOwner,
    initConfig: MicrophoneInitConfig
) : Sensor {

    private val internalStorage = context.filesDir

    private val minBufferSize: Int =
        AudioRecord.getMinBufferSize(
            initConfig.sampleRate,
            initConfig.channelConfig,
            initConfig.audioFormat
        )

    private val audioRecord: AudioRecord
    private lateinit var internalListener: Sensor.InternalSensorListener
    private lateinit var currentCaptureRequest: MicrophoneCaptureRequest

    private var isStarted = AtomicBoolean(false)

    init {
        with(initConfig) {
            audioRecord =
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    minBufferSize * 2 // Larger buffer to avoid audio glitches
                )
        }
    }

    override suspend fun prepare(internalSensorListener: Sensor.InternalSensorListener) {
        if (isStarted()) {
            Timber.w("Call to #prepare the capture is redundant as Sensor is currently capturing.")
            return
        }
        internalListener = internalSensorListener
    }

    override suspend fun start(captureRequest: CaptureRequest) {
        if (isStarted()) {
            throw IllegalStateException(
                "Call to #start capturing is redundant as Sensor is currently capturing."
            )
        }
        if (captureRequest !is MicrophoneCaptureRequest) {
            throw IllegalStateException(
                "Invalid Request. MicrophoneSensor needs a MicrophoneCaptureRequest. Given = ${captureRequest::class.java}"
            )
        }
        currentCaptureRequest = captureRequest
        File(internalStorage, currentCaptureRequest.outputFolder).mkdirs()

        internalListener.onStarted(InternalSensorType.MICROPHONE)
        audioRecord.startRecording()
        isStarted.set(true)

        withContext(Dispatchers.IO) {
            FileOutputStream(getDataFile()).use { outputStream ->
                val audioData = ByteArray(minBufferSize)

                while (isStarted()) {
                    val read = audioRecord.read(audioData, 0, minBufferSize)
                    if (read != AudioRecord.ERROR_INVALID_OPERATION) {
                        outputStream.write(audioData, 0, read)
                    }
                }
                val read = audioRecord.read(audioData, 0, minBufferSize)
                if (read != AudioRecord.ERROR_INVALID_OPERATION) {
                    outputStream.write(audioData, 0, read)
                }
            }
        }
    }

    override suspend fun pause() {
        TODO("Not yet implemented")
    }

    override suspend fun resume() {
        TODO("Not yet implemented")
    }

    override suspend fun stop() {
        if (isStarted()) {
            internalStop()
            internalListener.onStopped(InternalSensorType.MICROPHONE)
        }
    }

    private suspend fun internalStop() {
        if (isStarted()) {
            isStarted.set(false)
            audioRecord.stop()
        }
    }

    override suspend fun reset() {
        audioRecord.release()
    }

    override fun cancel() = runBlocking {
        internalStop()
        internalListener.onCancelled(InternalSensorType.MICROPHONE)
    }

    override fun getSensor() = audioRecord

    override fun isStarted() = isStarted.get()

    override fun getCaptureMode() = CaptureMode.ACTIVE

    private fun getDataFile(): File {
        with(currentCaptureRequest) {
            val filename = "${System.currentTimeMillis()}.$outputFormat"
            val filePath = "$outputFolder/$filename"
            return File(internalStorage, filePath)
        }
    }
}

data class MicrophoneInitConfig(
    val sampleRate: Int = 44100, // Sample rate in Hz (common choices: 44100, 48000)
    val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO, // Mono or Stereo
    val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT // 8-b
) : InitConfig(CaptureMode.ACTIVE)

// Not used currently
data class MicrophoneCaptureRequest(
    override val externalIdentifier: String,
    override val outputFolder: String,
    override val outputFormat: String = "audio/3gpp",
    override val outputTitle: String,
) : CaptureRequest(externalIdentifier, outputFolder, outputFormat, outputTitle)

internal object MicrophoneSensorFactory : SensorFactory {
    override fun create(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        initConfig: InitConfig,
    ) = MicrophoneSensor(context, lifecycleOwner, initConfig as MicrophoneInitConfig)
}