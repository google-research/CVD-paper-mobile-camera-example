package com.google.android.sensory.sensing_sdk.capture

import android.app.Application
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.ColorSpaceTransform
import android.hardware.camera2.params.RggbChannelVector
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.lifecycle.AndroidViewModel

@ExperimentalCamera2Interop
class CaptureViewModel(application: Application) : AndroidViewModel(application) {
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
          CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF
        ) // Set an identity correction matrix for color correction.
        .setCaptureRequestOption(
          CaptureRequest.COLOR_CORRECTION_MODE,
          CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX
        )
        .setCaptureRequestOption(
          CaptureRequest.COLOR_CORRECTION_TRANSFORM, ColorSpaceTransform(elements)
        ) // Set the individual channel gains.
        .setCaptureRequestOption(
          CaptureRequest.COLOR_CORRECTION_GAINS,
          RggbChannelVector(redGain, greenGain, greenGain, blueGain)
        ) // Set the manual gamma value.
        .setCaptureRequestOption(
          CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_GAMMA_VALUE
        )
        .setCaptureRequestOption(CaptureRequest.TONEMAP_GAMMA, gamma)
        .setCaptureRequestOption(
          CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON
        )
        .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, lockExposure)
    return optionsBuilder.build()
  }
}