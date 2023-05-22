import androidx.fragment.app.DialogFragment
import com.google.android.sensory.sensing_sdk.capture.model.CaptureSettings

// package com.google.android.sensory.sensing_sdk.capture
//
// import android.app.AlertDialog
// import android.content.Context
// import android.content.DialogInterface
// import android.hardware.camera2.CaptureRequest
// import android.hardware.camera2.CaptureResult
// import android.hardware.camera2.TotalCaptureResult
// import android.hardware.camera2.params.ColorSpaceTransform
// import android.hardware.camera2.params.RggbChannelVector
// import android.os.Bundle
// import android.os.CountDownTimer
// import android.view.LayoutInflater
// import android.view.View
// import android.view.ViewGroup
// import android.widget.TextView
// import androidx.camera.camera2.interop.Camera2CameraControl
// import androidx.camera.camera2.interop.CaptureRequestOptions
// import androidx.camera.core.CameraSelector
// import androidx.camera.core.TorchState
// import androidx.camera.view.PreviewView
// import androidx.core.content.ContextCompat
// import androidx.fragment.app.DialogFragment
// import androidx.fragment.app.Fragment
// import androidx.lifecycle.MutableLiveData
// import com.google.android.material.floatingactionbutton.FloatingActionButton
// import com.google.android.sensory.R
// import com.google.android.sensory.sensing_sdk.capture.model.CaptureSettings
// import com.google.common.util.concurrent.ListenableFuture
// import com.google.camera.libraries.common.sensing.research.fitbit.CameraXSensorV2
// import com.google.camera.libraries.common.sensing.research.fitbit.SharedImageProxy
// import com.google.camera2.camera.libraries.common.sensing.research.fitbit.Camera2TsvWriters
// import com.google.storage.camera.libraries.common.sensing.research.fitbit.WriteJpegFutureSubscriber
// import com.google.flow.libraries.common.sensing.research.fitbit.AbstractUnboundedSubscriber
// import com.google.flow.libraries.common.sensing.research.fitbit.FlowGate
// import com.google.storage.libraries.common.sensing.research.fitbit.StreamToTsvSubscriber
// import com.google.storage.libraries.common.sensing.research.fitbit.TsvWriter
// import java.io.File
// import java.util.Locale
// import java.util.Timer
// import java.util.TimerTask
// import java.util.concurrent.TimeUnit
// import org.reactivestreams.Publisher
// import org.reactivestreams.Subscriber
//
// /**
//  * Starter example for a simple camera data collection screen. This fragment is fully self-contained
//  * and can be placed directly into any activity.
//  *
//  *
//  * This example demonstrates a number of common patterns in camera data collection apps:
//  *
//  *
//  *  * Configuring camerax with a viewfinder using [CameraXSensor]
//  *  * Providing live UI updates from a [Publisher] using [MutableLiveData]
//  *  * Streaming data to disk with out-of-the-box [Subscriber]s
//  *  * Using [FlowGate] to start and stop a recording on button press
//  *
//  */
// class StarterCameraFragment(captureSettings: CaptureSettings) : DialogFragment() {
//   private val frameNumberData: MutableLiveData<Long>
//   private var frameNumber: Long = 0
//   private val recordedFramesData: MutableLiveData<Long>
//   private var recordedFrames: Long = 0
//   private val recordingGate: FlowGate
//   private var camera: CameraXSensorV2? = null
//   private var listener: IListener? = null
//   private var isPhoneSafeToUse = false
//   private var tvTimer: TextView? = null
//   private var countDownTimer: CountDownTimer? = null
//   var recordFab: FloatingActionButton? = null
//
//   init {
//     frameNumberData = MutableLiveData()
//     recordedFramesData = MutableLiveData()
//     recordingGate = FlowGate.createClosed()
//   }
//
//   override fun onCreate(savedInstanceState: Bundle?) {
//     super.onCreate(savedInstanceState)
//     camera = CameraXSensorV2.builder()
//       .setContext(requireContext())
//       .setCameraSelector(CameraSelector.DEFAULT_BACK_CAMERA)
//       .build()
//
//     // Count the number of frames
//     camera!!
//       .dataPublisher()
//       .subscribe(
//         AbstractUnboundedSubscriber.of { unusedImage ->
//           frameNumber++
//           frameNumberData.postValue(frameNumber)
//         })
//     showOverheatDialog()
//   }
//
//   private fun getCaptureRequestOptions(lockExposure: Boolean): CaptureRequestOptions {
//     // https://developer.android.com/reference/android/hardware/camera2/params/ColorSpaceTransform#ColorSpaceTransform(int[])
//     // 3*3 identity matrix represented in numerator, denominator format
//     val elements = intArrayOf(1, 1, 0, 1, 0, 1, 0, 1, 1, 1, 0, 1, 0, 1, 0, 1, 1, 1)
//     // Set gains to give approximately similar gains for all color channels.
//     val redGain = 0.5f
//     val greenGain = 1.7f
//     val blueGain = 3.0f
//     // Disable gamma by setting the exponent to 1.
//     val gamma = 1.0f
//     val optionsBuilder: CaptureRequestOptions.Builder =
//       CaptureRequestOptions.Builder() // Disable white balancing so that we can control it manually.
//         .setCaptureRequestOption(
//           CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF
//         ) // Set an identity correction matrix for color correction.
//         .setCaptureRequestOption(
//           CaptureRequest.COLOR_CORRECTION_MODE,
//           CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX
//         )
//         .setCaptureRequestOption(
//           CaptureRequest.COLOR_CORRECTION_TRANSFORM, ColorSpaceTransform(elements)
//         ) // Set the individual channel gains.
//         .setCaptureRequestOption(
//           CaptureRequest.COLOR_CORRECTION_GAINS,
//           RggbChannelVector(redGain, greenGain, greenGain, blueGain)
//         ) // Set the manual gamma value.
//         .setCaptureRequestOption(
//           CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_GAMMA_VALUE
//         )
//         .setCaptureRequestOption(CaptureRequest.TONEMAP_GAMMA, gamma)
//         .setCaptureRequestOption(
//           CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON
//         )
//         .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, lockExposure)
//     return optionsBuilder.build()
//   }
//
//   private fun lockExposure() {
//     // Get the current camera characteristics
//     val camera2Control: Camera2CameraControl =
//       Camera2CameraControl.from(camera.camera().get().getCameraControl())
//     val unusedFuture: ListenableFuture<Void> =
//       camera2Control.setCaptureRequestOptions(getCaptureRequestOptions(true))
//   }
//
//   // Safe to ignore CameraControl futures
//   override fun onCreateView(
//     inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
//   ): View {
//     val view: View = inflater.inflate(R.layout.starter_camera_fragment, container, false)
//
//     // Start the camera and preview
//     val previewView: PreviewView = view.findViewById(R.id.preview_view)
//     camera
//       .bindToLifecycle(this.viewLifecycleOwner)
//       .addListener(
//         {
//           camera.getPreview().get().setSurfaceProvider(previewView.surfaceProvider)
//           camera!!.camera!!.cameraControl.enableTorch(true)
//           val camera2Control: Camera2CameraControl =
//             Camera2CameraControl.from(camera!!.camera!!.cameraControl)
//           camera2Control.setCaptureRequestOptions(getCaptureRequestOptions(false))
//         },
//         ContextCompat.getMainExecutor(requireContext())
//       )
//
//     // Update the frame number display
//     /*TextView frameNumberText = view.findViewById(R.id.frame_number_text);
//     frameNumberData.observe(
//         this.getViewLifecycleOwner(), (n) -> frameNumberText.setText("Frame number: " + n));
//     TextView recordedFramesText = view.findViewById(R.id.recorded_frames_text);
//     recordedFramesData.observe(
//         this.getViewLifecycleOwner(), (n) -> recordedFramesText.setText("Recorded frames: " + n));*/recordFab =
//       view.findViewById<FloatingActionButton>(R.id.record_fab)
//     recordFab.setOnClickListener(
//       View.OnClickListener { v: View? ->
//         if (recordingGate.isOpen()) {
//           stopRecording()
//         } else if (!isPhoneSafeToUse) {
//           showOverheatDialog()
//         } else {
//           val timer = Timer()
//           timer.schedule(
//             object : TimerTask() {
//               override fun run() {
//                 lockExposure()
//               }
//             },
//             LOCK_AFTER_MS
//           )
//           // Get stream of images while recording
//           val recordingImages: Publisher<SharedImageProxy> =
//             recordingGate.passThrough(camera!!.dataPublisher())
//
//           // Compatibility layer between camerax ImageProxy and camera2 Image
//           SharedImageProxy.asImagePublisher(recordingImages) // Write from stream to disk as JPEGs
//             .subscribe(
//               WriteJpegFutureSubscriber.builder()
//                 .setFileSupplier { jpegFile }
//                 .setTotalFrames(Long.MAX_VALUE)
//                 .build())
//           val captureResultStream: Publisher<TotalCaptureResult> =
//             recordingGate.passThrough(camera.capturePublisher())
//           val cameraMetadataSaver: StreamToTsvSubscriber<CaptureResult> =
//             StreamToTsvSubscriber.builder<CaptureResult> ()
//               .setTsvWriter(TSV_WRITER)
//               .setSingleFile(tsvFile)
//               .build()
//           captureResultStream.subscribe(cameraMetadataSaver)
//
//           // Open the recording stream
//           recordingGate.open()
//           recordFab.setImageResource(R.drawable.quantum_gm_ic_videocam_off_vd_theme_24)
//           countDownTimer!!.start()
//         }
//       })
//     val toggleFlash = view.findViewById<FloatingActionButton>(R.id.toggle_flash)
//     toggleFlash.setOnClickListener { v: View? ->
//       camera!!.camera
//         .ifPresent { c ->
//           if (c.getCameraInfo().getTorchState().getValue() === TorchState.ON) {
//             // Turn off flash
//             c.getCameraControl().enableTorch(false)
//             toggleFlash.setImageResource(
//               R.drawable.quantum_gm_ic_flashlight_off_vd_theme_24
//             )
//           } else {
//             // Turn on flash
//             c.getCameraControl().enableTorch(true)
//             toggleFlash.setImageResource(
//               R.drawable.quantum_gm_ic_flashlight_on_vd_theme_24
//             )
//           }
//         }
//     }
//     tvTimer = view.findViewById<TextView>(R.id.tv_timer)
//     tvTimer!!.text = "00 : 30"
//     initializeTimer()
//     return view
//   }
//
//   private val jpegFile: File
//     get() {
//       val file: File = listener.createFile("ppg_$recordedFrames", ".jpg")
//       com.google.medical.discovery.pathapp.StarterCameraFragment.Companion.logger.atInfo()
//         .log("Record to %s", file.absolutePath)
//       recordedFrames++
//       recordedFramesData.postValue(recordedFrames)
//       return file
//     }
//   private val tsvFile: File
//     private get() = listener.createFile("ppg", ".tsv")
//
//   private fun showOverheatDialog() {
//     val builder = AlertDialog.Builder(getActivity())
//     builder
//       .setTitle(R.string.overheat_dialog_title)
//       .setMessage(R.string.overheat_dialog_message)
//       .setPositiveButton(
//         R.string.overheat_ok_to_proceed,
//         DialogInterface.OnClickListener { dialog, which -> isPhoneSafeToUse = true })
//       .setCancelable(false)
//     val dialog = builder.create()
//     dialog.show()
//   }
//
//   private fun initializeTimer() {
//     // Initialize timer
//     countDownTimer = object : CountDownTimer(30000, 1000) {
//       override fun onTick(millisUntilFinished: Long) {
//         val strDuration = String.format(
//           Locale.ENGLISH,
//           "%02d : %02d",
//           0L /* minutes */,
//           TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished)
//         )
//         tvTimer!!.text = strDuration
//         if (strDuration == "00 : 00" && recordingGate.isOpen()) {
//           stopRecording()
//         }
//       }
//
//       override fun onFinish() {}
//     }
//   }
//
//   private fun stopRecording() {
//     // Stop recording
//     recordingGate.completeAndClose()
//     recordFab!!.setImageResource(R.drawable.quantum_gm_ic_videocam_vd_theme_24)
//     val ppgCompleteFragment: Fragment = PpgCompleteFragment()
//     listener.changeFragment(ppgCompleteFragment)
//   }
//
//   override fun onAttach(context: Context) {
//     super.onAttach(context)
//     listener = if (context is IListener) {
//       context as IListener
//     } else {
//       throw RuntimeException("$context must implement IChangeFragment")
//     }
//   }
//
//   override fun onDetach() {
//     super.onDetach()
//     listener = null
//   }
//
//   companion object {
//     const val LOCK_AFTER_MS = 1000L
//     private val TSV_WRITER: TsvWriter<CaptureResult> = Camera2TsvWriters.captureResultBuilder()
//       .addFrameNumberColumn()
//       .addColumn(CaptureResult.SENSOR_TIMESTAMP)
//       .addColumn(CaptureResult.SENSOR_FRAME_DURATION)
//       .addColumn(CaptureResult.FLASH_MODE)
//       .addColumn(CaptureResult.FLASH_STATE)
//       .addColumn(CaptureResult.SENSOR_EXPOSURE_TIME)
//       .addColumn(CaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST)
//       .addColumn(CaptureResult.CONTROL_AE_MODE)
//       .addColumn(CaptureResult.CONTROL_AE_LOCK)
//       .addColumn(CaptureResult.CONTROL_AE_ANTIBANDING_MODE)
//       .addColumn(CaptureResult.CONTROL_AE_STATE)
//       .addColumn(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION)
//       .addColumn(CaptureResult.SENSOR_SENSITIVITY)
//       .addRangeColumn(CaptureResult.CONTROL_AE_TARGET_FPS_RANGE)
//       .addColumn(CaptureResult.CONTROL_AWB_MODE)
//       .addColumn(CaptureResult.CONTROL_AWB_STATE)
//       .addColumn(CaptureResult.COLOR_CORRECTION_MODE)
//       .addRggbChannelVectorColumn(CaptureResult.COLOR_CORRECTION_GAINS)
//       .addColumn(CaptureResult.CONTROL_AF_MODE)
//       .addColumn(CaptureResult.CONTROL_EFFECT_MODE)
//       .addColumn(CaptureResult.NOISE_REDUCTION_MODE)
//       .addColumn(CaptureResult.SHADING_MODE)
//       .addColumn(CaptureResult.TONEMAP_MODE)
//       .build()
//   }
// }


class StarterCameraFragment(captureSettings: CaptureSettings, onCaptureComplete: (() -> Unit)): DialogFragment() {
}