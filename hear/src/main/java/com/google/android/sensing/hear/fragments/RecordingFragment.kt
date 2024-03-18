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

package com.google.android.sensing.hear.fragments

import android.content.res.ColorStateList
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.sensing.SensorManager
import com.google.android.sensing.capture.sensors.MicrophoneCaptureRequest
import com.google.android.sensing.hear.HearApplication
import com.google.android.sensing.hear.R
import com.google.android.sensing.model.CaptureInfo
import com.google.android.sensing.model.InternalSensorType
import com.google.android.sensing.model.ProcessedInfo
import java.time.Instant
import kotlinx.coroutines.launch
import timber.log.Timber

/** Fragment for the component list. */
class RecordingFragment : Fragment(R.layout.fragment_recording) {

  private lateinit var recordHeadingTextView: TextView
  private lateinit var instructionTextView: TextView
  private lateinit var layoutRecorder: ConstraintLayout
  private lateinit var ivAudioState: AppCompatImageView
  private lateinit var insightGenerateProgress: CircularProgressIndicator
  private lateinit var audioRecordProgress: LinearProgressIndicator
  private lateinit var currentTimeTextView: TextView
  private lateinit var totalTimeTextView: TextView
  private lateinit var reviewButton: Button
  private lateinit var startButton: Button
  private lateinit var cancelButton: Button
  private lateinit var submitButton: Button

  private lateinit var countDownTimer: CountDownTimer

  private lateinit var sensorManager: SensorManager

  private val recordingResult = MutableLiveData<String?>()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    recordHeadingTextView = view.findViewById(R.id.record_heading)
    instructionTextView = view.findViewById(R.id.instruction_body)
    layoutRecorder = view.findViewById(R.id.layout_recorder)
    ivAudioState = view.findViewById(R.id.iv_audio_state)
    insightGenerateProgress = view.findViewById(R.id.insight_generate_progress)
    audioRecordProgress = view.findViewById(R.id.aduio_record_progress)
    currentTimeTextView = view.findViewById(R.id.current_time)
    totalTimeTextView = view.findViewById(R.id.total_time)
    startButton = view.findViewById(R.id.button_start_recording)
    reviewButton = view.findViewById(R.id.button_review)
    cancelButton = view.findViewById(R.id.button_cancel)
    submitButton = view.findViewById(R.id.button_submit)
    sensorManager = HearApplication.getSensorManager(requireContext())
  }

  override fun onResume() {
    super.onResume()

    // wait for sensorManager to be available for MICROPHONE
    resetView()
    setListeners()
  }

  private fun resetView() {
    recordHeadingTextView.text = resources.getString(R.string.record_heading)
    instructionTextView.visibility = View.VISIBLE
    startButton.visibility = View.VISIBLE
    layoutRecorder.visibility = View.GONE
    insightGenerateProgress.visibility = View.GONE
    cancelButton.visibility = View.GONE
    submitButton.visibility = View.GONE
    audioRecordProgress.max = 5000
    audioRecordProgress.progress = 0
    currentTimeTextView.text = "0.0 sec"
    totalTimeTextView.text = "5 sec"
    ivAudioState.setImageResource(R.drawable.ic_audio_default)
    if (::countDownTimer.isInitialized) {
      countDownTimer.cancel()
      countDownTimer.onTick(5000)
    }
    cancelButton.setOnClickListener(null)
    submitButton.setOnClickListener(null)
  }

  private fun setListeners() {
    sensorManager.registerListener(
      InternalSensorType.MICROPHONE,
      object : SensorManager.AppDataCaptureListener {
        override fun onStart(captureInfo: CaptureInfo) {
          handleOnStart()
        }

        override fun onStopped(captureInfo: CaptureInfo) {
          handleOnStopped()
        }

        override fun onCancelled(captureInfo: CaptureInfo?) {
          resetView()
        }

        override fun onRecordSaved(captureInfo: CaptureInfo) {}

        override fun onPostProcessed(processedInfo: ProcessedInfo) {
          processedInfo.result?.let { recordingResult.postValue(it) }
            ?: Timber.w("Prediction not received. Have you configured the credentials correctly? ")
        }

        override fun onError(exception: Exception, captureInfo: CaptureInfo?) {
          Timber.w("Error in prediction. ")
          Toast.makeText(requireContext(), "Check internet connection.", Toast.LENGTH_LONG).show()
        }
      }
    )

    countDownTimer =
      object : CountDownTimer(5000L, 100) {
        override fun onTick(millisUntilFinished: Long) {
          audioRecordProgress.progress = 5000 - millisUntilFinished.toInt()
          val progressText = "%.1f".format((audioRecordProgress.progress / 1000.0))
          currentTimeTextView.text = "$progressText sec"
        }

        override fun onFinish() {
          currentTimeTextView.text = "5 sec"
          audioRecordProgress.progress = 5000
          stopRecording()
        }
      }

    startButton.setOnClickListener {
      lifecycleScope.launch {
        sensorManager.start(
          InternalSensorType.MICROPHONE,
          MicrophoneCaptureRequest(
            externalIdentifier = "Patient1",
            outputFolder = "tbapp/recordings_${Instant.now()}",
            outputTitle = "Audio",
            outputFormat = "wav"
          )
        )
      }
    }
  }

  private fun handleOnStart() {
    countDownTimer.start()
    recordHeadingTextView.text = "Recording.."
    instructionTextView.visibility = View.GONE
    startButton.visibility = View.GONE
    submitButton.visibility = View.VISIBLE
    cancelButton.visibility = View.VISIBLE
    layoutRecorder.visibility = View.VISIBLE
    submitButton.backgroundTintList =
      ColorStateList.valueOf(
        ContextCompat.getColor(requireContext(), R.color.primary_disabled_grey)
      )
    submitButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.disabled_text_color))
    ivAudioState.setImageResource(R.drawable.ic_audio_recording)
    cancelButton.setOnClickListener {
      lifecycleScope.launch { sensorManager.cancel(InternalSensorType.MICROPHONE) }
    }
  }

  private fun handleOnStopped() {
    recordHeadingTextView.text = "Recording complete"
    if (::countDownTimer.isInitialized) countDownTimer.cancel()
    ivAudioState.setImageResource(R.drawable.ic_audio_complete)
    submitButton.backgroundTintList =
      ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.primary_blue_40))
    submitButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
    submitButton.setOnClickListener {
      recordHeadingTextView.text = "Analyzing..."
      layoutRecorder.visibility = View.GONE
      insightGenerateProgress.visibility = View.VISIBLE
      recordingResult.value?.let { goToInsightFragment(it) }
        ?: recordingResult.observe(viewLifecycleOwner) {
          it?.let {
            goToInsightFragment(it)
            recordingResult.value = null
          }
        }
    }
  }

  private fun goToInsightFragment(result: String) {
    findNavController()
      .navigate(R.id.action_recordingFragment_to_insightFragment, bundleOf("result" to result))
  }

  private fun stopRecording() {
    recordHeadingTextView.text = "Stopping Recording.."
    if (sensorManager.isStarted(InternalSensorType.MICROPHONE)) {
      lifecycleScope.launch { sensorManager.stop(InternalSensorType.MICROPHONE) }
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    if (sensorManager.isStarted(InternalSensorType.MICROPHONE)) {
      lifecycleScope.launch { sensorManager.cancel(InternalSensorType.MICROPHONE) }
    }
  }
}
