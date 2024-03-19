plugins {
  id(Plugins.BuildPlugins.androidLib)
  id(Plugins.BuildPlugins.kotlinAndroid)
  id(Plugins.BuildPlugins.kotlinKapt)
}

android {
  namespace = "com.google.android.sensing"
  compileSdk = Sdk.compileSdk

  defaultConfig {
    minSdk = Sdk.minSdk

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    consumerProguardFiles("consumer-rules.pro")
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  kotlin { jvmToolchain(17) }
}

dependencies {
  implementation(Dependencies.Androidx.coreKtx)
  implementation(Dependencies.Androidx.appCompat)
  implementation(Dependencies.Androidx.fragmentKtx)
  implementation(Dependencies.Lifecycle.liveDataKtx)
  implementation(Dependencies.Navigation.navFragmentKtx)
  implementation(Dependencies.Lifecycle.service)
  implementation(Dependencies.material)
  implementation(Dependencies.timber)
  implementation(Dependencies.Androidx.workRuntimeKtx)
  implementation(Dependencies.Kotlin.kotlinCoroutinesAndroid)
  implementation(Dependencies.Room.ktx)
  implementation(Dependencies.Room.runtime)
  implementation(Dependencies.sqlcipher)
  implementation(Dependencies.CameraX.core)
  implementation(Dependencies.CameraX.camera2)
  implementation(Dependencies.CameraX.cameraLifecycle)
  implementation(Dependencies.CameraX.cameraVideo)
  implementation(Dependencies.CameraX.cameraView)
  implementation(Dependencies.gson)

  implementation(Dependencies.ReactiveStreams.reactiveStreams)
  implementation(Dependencies.ReactiveStreams.lifecycle)

  implementation(Dependencies.Minio.minio)
  implementation(Dependencies.Minio.Extra.poiOoxml)
  implementation(Dependencies.Minio.Extra.xmlbeans)
  implementation(Dependencies.Minio.Extra.staxApi)
  implementation(Dependencies.Minio.Extra.aaltoXml)

  kapt(Dependencies.Room.compiler)

  testImplementation(Dependencies.junit)
  androidTestImplementation(Dependencies.AndroidxTest.extJunit)
  androidTestImplementation(Dependencies.Espresso.espressoCore)
}
