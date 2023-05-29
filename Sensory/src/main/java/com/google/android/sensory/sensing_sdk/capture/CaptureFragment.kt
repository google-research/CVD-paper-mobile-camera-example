package com.google.android.sensory.sensing_sdk.capture

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.hardware.camera2.CaptureResult
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
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
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.sensory.R
import com.google.android.sensory.sensing_sdk.model.CaptureInfo
import com.google.android.sensory.sensing_sdk.model.CaptureType
import com.google.android.sensory.sensing_sdk.model.SensorType
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.fitbit.research.sensing.common.libraries.camera.Camera2InteropActions
import com.google.fitbit.research.sensing.common.libraries.camera.Camera2InteropSensor
import com.google.fitbit.research.sensing.common.libraries.camera.CameraXSensorV2
import com.google.fitbit.research.sensing.common.libraries.camera.SharedImageProxy
import com.google.fitbit.research.sensing.common.libraries.camera.camera2.Camera2TsvWriters
import com.google.fitbit.research.sensing.common.libraries.camera.storage.WriteJpegFutureSubscriber
import com.google.fitbit.research.sensing.common.libraries.flow.FlowGate
import com.google.fitbit.research.sensing.common.libraries.storage.StreamToTsvSubscriber
import java.io.File
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


/** God-like fragment dealing with all sensors: For now only camera with capture types being VIDEO_PPG and IMAGE.
 * This is done by programmatically inflating the layout based on the captureType given.
 * Stores captureId in result.
 * Reason why all views are managed by this fragment is for a given capture type, multiple sensors may be required.
 * TODO: This is too customised and we will need to make it configurable. Configurability options:-
 * 1. CaptureRequestOptions could be a part of [captureInfo.captureSettings]
 * 2. WriteJpegFutureSubscriber could be any generic subscriber
 * 3. Video timer should not be hardcoded
 * 4. Other camera settings like DEFAULT_BACK_CAMERA or something else
 * 5. TSVWriter can be configurable
 * 6. File suppliers should be more generic ==>> DONE
 * */
@SuppressLint("UnsafeOptInUsageError")
class CaptureFragment(
  private val captureInfo: CaptureInfo,
  private val onCaptureComplete: ((CaptureInfo) -> String),
): Fragment() {

  private val captureViewModel: CaptureViewModel by viewModels()

  private lateinit var recordingGate: FlowGate
  private var preview: Preview? = null
  private var camera: Camera2InteropSensor? = null
  private var isPhoneSafeToUse = false
  private var countDownTimer: CountDownTimer? = null

  private lateinit var previewView: PreviewView
  private lateinit var recordFab: FloatingActionButton
  private lateinit var toggleFlashFab: FloatingActionButton
  private lateinit var recordTimer: TextView
  private lateinit var btnTakePhoto: Button

  private var captured = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    /** For a different [CaptureType] sensors will be initialized and subscribed differently. */
    when(captureInfo.captureType){
      CaptureType.VIDEO_PPG-> {
        preview = Preview.Builder().build()
        camera = Camera2InteropSensor.builder()
          .setContext(requireContext())
          .setBoundLifecycle(this)
          .setCameraXSensorBuilder(
            CameraXSensorV2.builder()
              .setCameraSelector(CameraSelector.DEFAULT_BACK_CAMERA)
              .addUseCase(preview)
          )
          .build()
        recordingGate = FlowGate.createClosed()
        showOverheatDialog()
      }
      CaptureType.IMAGE -> {
        // Following camera initialization is same as above but it will depend on settings in CaptureInfo and hence not clubbed.
        preview = Preview.Builder().build()
        camera = Camera2InteropSensor.builder()
          .setContext(requireContext())
          .setBoundLifecycle(this)
          .setCameraXSensorBuilder(
            CameraXSensorV2.builder()
              .setCameraSelector(CameraSelector.DEFAULT_BACK_CAMERA)
              .addUseCase(preview)
          )
          .build()
      }
    }
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View? {
    requireActivity().window.decorView.fitsSystemWindows = true
    (requireActivity() as AppCompatActivity).supportActionBar?.hide()
    val layout =
      when(captureInfo.captureType){
        CaptureType.VIDEO_PPG -> R.layout.fragment_video_ppg
        CaptureType.IMAGE -> R.layout.fragment_image
      }
    val view = inflater.inflate(layout, container, false)
    when(captureInfo.captureType){
      CaptureType.VIDEO_PPG -> {
        previewView = view.findViewById(R.id.preview_view)
        recordFab = view.findViewById(R.id.record_fab)
        toggleFlashFab = view.findViewById(R.id.toggle_flash_fab)
        recordTimer = view.findViewById(R.id.record_timer)
      }
      CaptureType.IMAGE -> {
        previewView = view.findViewById(R.id.preview_view)
        toggleFlashFab = view.findViewById(R.id.toggle_flash_fab)
        btnTakePhoto = view.findViewById(R.id.btn_take_photo)
      }
    }
    return view
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    when(captureInfo.captureType){
      CaptureType.VIDEO_PPG -> {
        // Start the camera and preview
        preview!!.setSurfaceProvider(previewView.surfaceProvider)
        camera!!
          .lifecycle
          .addObserver(
            object : DefaultLifecycleObserver {
              override fun onCreate(owner: LifecycleOwner) {
                camera!!.cameraXSensor.cameraControl!!.enableTorch(true)
                camera!!.camera2Control!!.captureRequestOptions = captureViewModel.getCaptureRequestOptions(false)
              }
            })
        recordTimer.text = "00 : 30"
        initializeTimer()
        recordFab.setOnClickListener{
          processRecord()
        }
        toggleFlashFab.setOnClickListener {
          CaptureUtil.toggleFlash(camera!!, toggleFlashFab)
        }
      }
      CaptureType.IMAGE -> {
        preview!!.setSurfaceProvider(previewView.surfaceProvider)
        camera!!.lifecycle
          .addObserver(
            object : DefaultLifecycleObserver {
              override fun onCreate(owner: LifecycleOwner) {
                camera!!.cameraXSensor.cameraControl!!.enableTorch(true)
                camera!!.camera2Control!!.captureRequestOptions = CaptureRequestOptions.Builder().build()
              }
            })
        btnTakePhoto.setOnClickListener { capturePhoto() }
        toggleFlashFab.setOnClickListener {
          CaptureUtil.toggleFlash(camera!!, toggleFlashFab)
        }
      }
    }
  }

  private fun processRecord(){
    if (recordingGate.isOpen) {
      stopRecording()
    } else if (!isPhoneSafeToUse) {
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
      // Get stream of images while recording
      val recordingImages =
        recordingGate.passThrough(
          camera!!.dataPublisher()
        )

      // Compatibility layer between camerax ImageProxy and camera2 Image
      SharedImageProxy.asImagePublisher(recordingImages) // Write from stream to disk as JPEGs
        .subscribe(
          WriteJpegFutureSubscriber.builder()
            .setFileSupplier { getCameraResourceFile() }
            .setTotalFrames(Long.MAX_VALUE)
            .build()
        )
      val captureResultStream =
        recordingGate.passThrough(
          camera!!.captureResultPublisher()
        )
      val cameraMetadataSaver =
        StreamToTsvSubscriber.builder<CaptureResult>()
          .setTsvWriter(TSV_WRITER)
          .setSingleFile(getCameraMetadataFile())
          .build()
      captureResultStream.subscribe(cameraMetadataSaver)

      // Open the recording stream
      recordingGate.open()
      // recordFab!!.setImageResource(R.drawable.quantum_gm_ic_videocam_off_vd_theme_24)
      countDownTimer!!.start()
    }
  }

  private fun stopRecording() {
    recordingGate.completeAndClose()
    // recordFab!!.setImageResource(R.drawable.quantum_gm_ic_videocam_vd_theme_24)
    finishCapturing()
  }

  // Safe to ignore CameraControl futures
  private fun lockExposure() {
    camera!!.camera2Control!!.captureRequestOptions = captureViewModel.getCaptureRequestOptions(true)
  }

  private fun showOverheatDialog() {
    val builder = AlertDialog.Builder(requireActivity())
    builder
      .setTitle(R.string.overheat_dialog_title)
      .setMessage(R.string.overheat_dialog_message)
      .setPositiveButton(
        R.string.overheat_ok_to_proceed
      ) { dialog, which -> isPhoneSafeToUse = true }
      .setCancelable(false)
    val dialog = builder.create()
    dialog.show()
  }

  private fun initializeTimer() {
    countDownTimer = object : CountDownTimer(30000, 1000) {
      override fun onTick(millisUntilFinished: Long) {
        val strDuration = String.format(
          Locale.ENGLISH,
          "%02d : %02d",
          0L /* minutes */,
          TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished)
        )
        recordTimer.text = strDuration
        if (millisUntilFinished == 0L && recordingGate.isOpen) {
          stopRecording()
        }
      }
      override fun onFinish() {}
    }
  }

  // Safe to ignore CameraControl futures
  private fun capturePhoto() {
    Futures.addCallback(
      Camera2InteropActions.captureSingleJpegWithMetadata(
        camera, { getCameraResourceFile() }, { getCameraMetadataFile() }, Executors.newSingleThreadExecutor()
      ),
      object : FutureCallback<Boolean?> {
        override fun onSuccess(success: Boolean?) {
          Toast.makeText(requireContext(), "Saved photo", Toast.LENGTH_SHORT).show()
          finishCapturing()
        }

        override fun onFailure(t: Throwable) {
          Toast.makeText(requireContext(), "Failed to save photo: ", Toast.LENGTH_SHORT).show()
        }
      },
      ContextCompat.getMainExecutor(requireContext())
    )
  }

  private fun getCameraResourceFile(): File {
    val filePath = "${captureInfo.captureFolder}/${SensorType.CAMERA}/Participant${captureInfo.participantId}_${captureInfo.captureSettings.title}_data_${System.currentTimeMillis()}.${captureInfo.captureSettings.fileTypeMap[SensorType.CAMERA]}"
    val fileFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    return File( fileFolder, filePath)
  }

  private fun getCameraMetadataFile(): File {
    val filePath = "${captureInfo.captureFolder}/${SensorType.CAMERA}/Participant_${captureInfo.participantId}_${captureInfo.captureSettings.title}_metadata_${System.currentTimeMillis()}.${captureInfo.captureSettings.metaDataTypeMap[SensorType.CAMERA]}"
    val fileFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    return File( fileFolder, filePath)
  }

  private fun finishCapturing(){
    onCaptureComplete(captureInfo)
    captured = true
    setFragmentResult(CAPTURE_COMPLETE, bundleOf(CAPTURE_ID to captureInfo.captureId, CAPTURED to captured))
    requireActivity().supportFragmentManager.popBackStack()
  }

  override fun onDetach() {
    if(!captured) {
      setFragmentResult(CAPTURE_COMPLETE, bundleOf(CAPTURED to false))
    }
    requireActivity().window.decorView.fitsSystemWindows = false
    (requireActivity() as AppCompatActivity).supportActionBar?.show()
    super.onDetach()
  }

  companion object {
    const val LOCK_AFTER_MS = 1000
    const val CAPTURE_COMPLETE = "capture_complete"
    const val CAPTURED = "captured"
    const val TAG = "CAPTURE_FRAGMENT"
    const val CAPTURE_ID = "capture_id"
    private val TSV_WRITER = Camera2TsvWriters.captureResultBuilder()
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