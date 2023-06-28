plugins {
  id(Plugins.BuildPlugins.application)
  id(Plugins.BuildPlugins.kotlinAndroid)
  id(Plugins.BuildPlugins.kotlinKapt)
  id(Plugins.BuildPlugins.navSafeArgs)
}

android {
  namespace = "com.google.android.sensory"
  compileSdk = 33

  defaultConfig {
    minSdk = 26
    targetSdk = 33
    versionCode = 1
    versionName = "1.0"
    testInstrumentationRunner = Dependencies.androidJunitRunner
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions { jvmTarget = JavaVersion.VERSION_17.toString() }
  buildFeatures { viewBinding = true }
  packagingOptions { resources.excludes.addAll(listOf("META-INF/*")) }
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
  implementation(Dependencies.Glide.glide)
  implementation(Dependencies.CameraX.core)
  implementation(Dependencies.CameraX.camera2)
  implementation(Dependencies.CameraX.cameraLifecycle)
  implementation(Dependencies.CameraX.cameraVideo)
  implementation(Dependencies.CameraX.cameraView)

  implementation(Dependencies.ReactiveStreams.reactiveStreams)
  implementation(Dependencies.ReactiveStreams.lifecycle)

  // Following set of dependencies are not tested and may change
  // ========= minio =========
  implementation("io.minio:minio:8.5.2")
  // Minio is not out-of-the-box Android compatible. For fully working Minio following dependencies
  // were needed to be added [https://stackoverflow.com/a/66395017]
  implementation("org.apache.poi:poi-ooxml:3.17")
  implementation("org.apache.xmlbeans:xmlbeans:3.1.0")
  implementation("javax.xml.stream:stax-api:1.0")
  implementation("com.fasterxml:aalto-xml:1.2.2")
  // ========= minio =========

  // Following set of dependencies are not tested and may change
  // ========= fitbit.sensing.common.libraries =========//
  implementation("com.google.auto.value:auto-value-annotations:1.10.1")
  kapt("com.google.auto.value:auto-value:1.10.1")
  implementation("com.google.flogger:flogger:0.7.4")
  implementation("com.google.flogger:flogger-system-backend:0.7.4")
  implementation("org.apache.commons:commons-csv:1.10.0")
  // ========= fitbit.sensing.common.libraries =========//

  kapt(Dependencies.Room.compiler)

  // ========= FHIR SDC =========//
  implementation("com.google.android.fhir:data-capture:1.0.0")
  implementation("com.google.android.fhir:engine:0.1.0-beta03")
  // ========= FHIR SDC =========//

  testImplementation(Dependencies.junit)
  androidTestImplementation(Dependencies.AndroidxTest.extJunit)
  androidTestImplementation(Dependencies.Espresso.espressoCore)
}
