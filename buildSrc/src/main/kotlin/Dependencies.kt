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

object Dependencies {

  object Androidx {
    const val activity = "androidx.activity:activity:${Versions.Androidx.activity}"
    const val appCompat = "androidx.appcompat:appcompat:${Versions.Androidx.appCompat}"
    const val coreKtx = "androidx.core:core-ktx:${Versions.Androidx.coreKtx}"
    const val datastorePref =
      "androidx.datastore:datastore-preferences:${Versions.Androidx.datastorePref}"
    const val fragmentKtx = "androidx.fragment:fragment-ktx:${Versions.Androidx.fragmentKtx}"
    const val recyclerView = "androidx.recyclerview:recyclerview:${Versions.Androidx.recyclerView}"
    const val sqliteKtx = "androidx.sqlite:sqlite-ktx:${Versions.Androidx.sqliteKtx}"
    const val workRuntimeKtx = "androidx.work:work-runtime-ktx:${Versions.Androidx.workRuntimeKtx}"
  }

  object Kotlin {
    const val kotlinCoroutinesAndroid =
      "org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.Kotlin.kotlinCoroutinesCore}"
  }

  object Room {
    const val compiler = "androidx.room:room-compiler:${Versions.Androidx.room}"
    const val ktx = "androidx.room:room-ktx:${Versions.Androidx.room}"
    const val runtime = "androidx.room:room-runtime:${Versions.Androidx.room}"
  }

  object Lifecycle {
    const val liveDataKtx =
      "androidx.lifecycle:lifecycle-livedata-ktx:${Versions.Androidx.lifecycle}"
    const val service = "androidx.lifecycle:lifecycle-service:${Versions.Androidx.lifecycle}"
  }

  object CameraX {
    const val core = "androidx.camera:camera-core:${Versions.CameraX.cameraXVersion}"
    const val camera2 = "androidx.camera:camera-camera2:${Versions.CameraX.cameraXVersion}"
    const val cameraLifecycle =
      "androidx.camera:camera-lifecycle:${Versions.CameraX.cameraXVersion}"
    const val cameraVideo = "androidx.camera:camera-video:${Versions.CameraX.cameraXVersion}"
    const val cameraView = "androidx.camera:camera-view:${Versions.CameraX.cameraXVersion}"
  }

  object ReactiveStreams {
    const val reactiveStreams = "org.reactivestreams:reactive-streams:1.0.0"
    const val lifecycle = "androidx.lifecycle:lifecycle-reactivestreams-ktx:2.5.1"
  }

  object Navigation {
    const val navFragmentKtx =
      "androidx.navigation:navigation-fragment-ktx:${Versions.Androidx.navigation}"
  }

  object Glide {
    const val glide = "com.github.bumptech.glide:glide:${Versions.Glide.glide}"
  }

  const val guava = "com.google.guava:guava:${Versions.guava}"
  const val material = "com.google.android.material:material:${Versions.material}"
  const val sqlcipher = "net.zetetic:android-database-sqlcipher:${Versions.sqlcipher}"
  const val timber = "com.jakewharton.timber:timber:${Versions.timber}"

  object AndroidxTest {
    const val extJunit = "androidx.test.ext:junit:${Versions.AndroidxTest.extJunit}"
  }

  object Espresso {
    const val espressoCore = "androidx.test.espresso:espresso-core:${Versions.espresso}"
  }

  const val androidJunitRunner = "androidx.test.runner.AndroidJUnitRunner"
  const val junit = "junit:junit:${Versions.junit}"

  object Versions {
    object Androidx {
      const val activity = "1.2.1"
      const val appCompat = "1.1.0"
      const val constraintLayout = "2.1.1"
      const val coreKtx = "1.2.0"
      const val datastorePref = "1.0.0"
      const val fragmentKtx = "1.3.1"
      const val lifecycle = "2.2.0"
      const val navigation = "2.5.3"
      const val recyclerView = "1.1.0"
      const val room = "2.4.2"
      const val sqliteKtx = "2.1.0"
      const val workRuntimeKtx = "2.7.1"
    }

    object Glide {
      const val glide = "4.14.2"
    }

    object Kotlin {
      const val kotlinCoroutinesCore = "1.6.4"
      const val stdlib = "1.6.10"
    }

    object CameraX {
      const val cameraXVersion = "1.2.3"
    }

    const val guava = "28.2-android"
    const val material = "1.6.0"
    const val sqlcipher = "4.5.0"
    const val timber = "5.0.1"

    object AndroidxTest {
      const val extJunit = "1.1.3"
    }

    const val espresso = "3.4.0"
    const val junit = "4.13.2"
  }
}
