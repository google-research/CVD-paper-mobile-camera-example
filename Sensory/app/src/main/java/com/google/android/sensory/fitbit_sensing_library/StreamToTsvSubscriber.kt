package com.google.android.sensory.fitbit_sensing_library

import android.media.Image

interface StreamToTsvSubscriber {
  suspend fun onNext(image: Image)
}