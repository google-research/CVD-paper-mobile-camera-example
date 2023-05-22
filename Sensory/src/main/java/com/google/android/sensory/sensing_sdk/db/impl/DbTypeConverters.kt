package com.google.android.sensory.sensing_sdk.db.impl

import androidx.room.TypeConverter
import java.time.Instant

internal object DbTypeConverters {
  @JvmStatic @TypeConverter
  fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()

  @JvmStatic
  @TypeConverter
  fun longToInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }
}