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

import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
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
import java.time.Instant
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** Fragment for the component list. */
class RecordingFragment : Fragment(R.layout.fragment_recording) {

  private lateinit var recordHeadingTextView: TextView
  private lateinit var circularProgressIndicator: CircularProgressIndicator
  private lateinit var progressIndicator: LinearProgressIndicator
  private lateinit var currentTimeTextView: TextView
  private lateinit var totalTimeTextView: TextView
  private lateinit var reviewButton: Button
  private lateinit var cancelButton: Button
  private lateinit var submitButton: Button

  private lateinit var countDownTimer: CountDownTimer

  private lateinit var sensorManager: SensorManager

  private var autoMoveToInsightPageWhenResultAvailable = false
  private val recordingResult = MutableLiveData<String>()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    recordHeadingTextView = view.findViewById(R.id.record_heading)
    circularProgressIndicator = view.findViewById(R.id.circularProgressBar)
    progressIndicator = view.findViewById(R.id.progress)
    currentTimeTextView = view.findViewById(R.id.current_time)
    totalTimeTextView = view.findViewById(R.id.total_time)
    reviewButton = view.findViewById(R.id.button_review)
    cancelButton = view.findViewById(R.id.button_cancel)
    submitButton = view.findViewById(R.id.button_submit)
  }

  override fun onResume() {
    super.onResume()
    circularProgressIndicator.visibility = View.GONE
    progressIndicator.max = 5000
    progressIndicator.progress = 0
    totalTimeTextView.text = "5 sec"
    countDownTimer =
      object : CountDownTimer(5000L, 500) {
        override fun onTick(millisUntilFinished: Long) {
          progressIndicator.progress = 5000 - millisUntilFinished.toInt()
          println("Progress = " + progressIndicator.progress)
          val progressText = "%.1f".format((progressIndicator.progress / 1000.0))
          currentTimeTextView.text = "$progressText sec"
        }

        override fun onFinish() {
          currentTimeTextView.text = "5 sec"
          progressIndicator.progress = 5000
          stopRecording()
        }
      }

    sensorManager = HearApplication.getSensorManager(requireContext())

    lifecycleScope.launch {
      sensorManager.registerListener(
        InternalSensorType.MICROPHONE,
        object : SensorManager.AppDataCaptureListener {
          override fun onStart(captureInfo: CaptureInfo) {
            countDownTimer.start()
            recordHeadingTextView.text = "Recording.."
          }

          override fun onComplete(captureInfo: CaptureInfo) {
            recordHeadingTextView.text = "Recording Complete"
            if (::countDownTimer.isInitialized) countDownTimer.cancel()
            if (autoMoveToInsightPageWhenResultAvailable) {
              goToInsightFragment()
            }
          }

          override fun onPostProcessed(result: String) {
            recordingResult.postValue(result)
            Log.d("predictWithAudio", "result: $result")
          }

          override fun onError(exception: Exception, captureInfo: CaptureInfo?) {
            TODO("Not yet implemented")
          }
        }
      )
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

    cancelButton.setOnClickListener { stopRecording() }

    submitButton.setOnClickListener {
      if (sensorManager.isStarted(InternalSensorType.MICROPHONE)) {
        autoMoveToInsightPageWhenResultAvailable = true
        stopRecording()
      } else {
        autoMoveToInsightPageWhenResultAvailable = false
        goToInsightFragment()
      }
    }
  }

  private fun goToInsightFragment() {
    recordHeadingTextView.text = "Analyzing..."
    circularProgressIndicator.visibility = View.VISIBLE
    progressIndicator.visibility = View.GONE
    currentTimeTextView.visibility = View.GONE
    totalTimeTextView.visibility = View.GONE
    reviewButton.visibility = View.GONE
    recordingResult.observe(viewLifecycleOwner) {
      findNavController()
        .navigate(R.id.action_recordingFragment_to_insightFragment, bundleOf("result" to it))
    }
  }

  private fun stopRecording() {
    recordHeadingTextView.text = "Stopping Recording.."
    runBlocking { sensorManager.stop(InternalSensorType.MICROPHONE) }
  }

  override fun onStop() {
    super.onStop()
    runBlocking { sensorManager.stop(InternalSensorType.MICROPHONE) }
  }
}
