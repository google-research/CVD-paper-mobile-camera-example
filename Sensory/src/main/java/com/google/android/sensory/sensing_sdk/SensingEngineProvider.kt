package com.google.android.sensory.sensing_sdk

import android.content.Context
import com.google.android.sensory.sensing_sdk.db.impl.DatabaseImpl
import com.google.android.sensory.sensing_sdk.impl.SensingEngineImpl

object SensingEngineProvider {
  private lateinit var uploadConfiguration: UploadConfiguration
  private var sensingEngine: SensingEngine? = null
  fun init(uploadConfiguration: UploadConfiguration) {
    this.uploadConfiguration = uploadConfiguration
  }

  fun getOrCreateSensingEngine(context: Context, enableDatabaseEncryption: Boolean): SensingEngine {
    if (sensingEngine == null) {
      val database = DatabaseImpl(context, enableDatabaseEncryption)
      sensingEngine = SensingEngineImpl(database, context, uploadConfiguration)
    }
    return sensingEngine!!
  }
}