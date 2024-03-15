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

package com.google.android.sensing.capture.sample

import android.annotation.SuppressLint
import android.app.Application
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.ColorSpaceTransform
import android.hardware.camera2.params.RggbChannelVector
import android.os.CountDownTimer
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData

class CaptureViewModel(application: Application) : AndroidViewModel(application) {

  val isPhoneSafeToUse = MutableLiveData(false)
  private lateinit var countDownTimer: CountDownTimer
  val timerLiveData = MutableLiveData<Long>()
  val automaticallyStopCapturing = MutableLiveData<Boolean>()

  suspend fun startTimer() {
    countDownTimer =
      object : CountDownTimer(1000L * CameraCaptureFragment.IMAGE_STREAM_TIMER_SECONDS, 1000) {
          override fun onTick(millisUntilFinished: Long) {
            timerLiveData.postValue(millisUntilFinished)
          }
          override fun onFinish() {
            automaticallyStopCapturing.postValue(true)
          }
        }
        .start()
  }

  fun endTimer() {
    countDownTimer.cancel()
  }

  @SuppressLint("UnsafeOptInUsageError")
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
}
