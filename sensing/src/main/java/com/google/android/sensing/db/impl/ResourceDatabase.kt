/*
 * Copyright 2023 Google LLC
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

package com.google.android.sensing.db.impl

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.google.android.sensing.db.impl.dao.CaptureInfoDao
import com.google.android.sensing.db.impl.dao.ResourceMetaInfoDao
import com.google.android.sensing.db.impl.dao.UploadRequestDao
import com.google.android.sensing.db.impl.entities.CaptureInfoEntity
import com.google.android.sensing.db.impl.entities.ResourceMetaInfoEntity
import com.google.android.sensing.db.impl.entities.UploadRequestEntity

@Database(
  entities = [CaptureInfoEntity::class, ResourceMetaInfoEntity::class, UploadRequestEntity::class],
  version = 1,
  exportSchema = false
)
@TypeConverters(DbTypeConverters::class)
internal abstract class ResourceDatabase : RoomDatabase() {
  abstract fun captureInfoDao(): CaptureInfoDao
  abstract fun resourceMetaInfoDao(): ResourceMetaInfoDao
  abstract fun uploadRequestDao(): UploadRequestDao
}
