package com.google.android.sensory.sensing_sdk.capture.model

import com.google.android.sensory.sensing_sdk.model.CaptureInfo
import com.google.android.sensory.sensing_sdk.model.ResourceInfo

interface CaptureManagerListener {
  /** Notifies user that capturing has started.*/
  suspend fun onCaptureStart(captureInfo: CaptureInfo)

  /** Notifies change of state while capturing is in progress, for example, when an image is published in PPG. */
  suspend fun onCaptureStateChanged(resourceInfo: ResourceInfo)

  /** Notifies user that capturing has ended: returns captureId of the session.*/
  suspend fun onCaptureEnd(captureInfo: CaptureInfo)
}