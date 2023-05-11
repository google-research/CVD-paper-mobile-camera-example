package com.google.android.sensory.sensing_sdk.db.impl

import androidx.room.Database
import androidx.room.RoomDatabase
import com.google.android.sensory.sensing_sdk.db.impl.dao.CaptureInfoDao
import com.google.android.sensory.sensing_sdk.db.impl.dao.ResourceInfoDao
import com.google.android.sensory.sensing_sdk.db.impl.dao.UploadRequestDao
import com.google.android.sensory.sensing_sdk.db.impl.entities.CaptureInfoEntity
import com.google.android.sensory.sensing_sdk.db.impl.entities.ResourceInfoEntity
import com.google.android.sensory.sensing_sdk.db.impl.entities.UploadRequestEntity

@Database(
  entities = [
    CaptureInfoEntity::class,
    ResourceInfoEntity::class,
    UploadRequestEntity::class
  ],
  version = 1,
  exportSchema = true
)
internal abstract class ResourceDatabase: RoomDatabase(){
  abstract fun captureInfoDao(): CaptureInfoDao
  abstract fun resourceInfoDao(): ResourceInfoDao
  abstract fun uploadRequestDao(): UploadRequestDao
}