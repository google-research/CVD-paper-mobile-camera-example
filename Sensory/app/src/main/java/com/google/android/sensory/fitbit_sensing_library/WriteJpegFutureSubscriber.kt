package com.google.android.sensory.fitbit_sensing_library

import android.media.Image

interface WriteJpegFutureSubscriber {
  suspend fun onNext(image: Image)
}