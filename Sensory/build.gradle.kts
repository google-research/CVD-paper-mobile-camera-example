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

  implementation(Dependencies.Minio.minio)
  implementation(Dependencies.Minio.Extra.poiOoxml)
  implementation(Dependencies.Minio.Extra.xmlbeans)
  implementation(Dependencies.Minio.Extra.staxApi)
  implementation(Dependencies.Minio.Extra.aaltoXml)

  implementation(Dependencies.FitbitSensingLibraryDeps.autoValueAnnotation)
  implementation(Dependencies.FitbitSensingLibraryDeps.flogger)
  implementation(Dependencies.FitbitSensingLibraryDeps.floggerBackend)
  implementation(Dependencies.FitbitSensingLibraryDeps.commonsCsv)
  kapt(Dependencies.FitbitSensingLibraryDeps.autoValue)

  kapt(Dependencies.Room.compiler)

  implementation(Dependencies.AndroidFhir.dataCapture)
  implementation(Dependencies.AndroidFhir.fhirEngine)

  testImplementation(Dependencies.junit)
  androidTestImplementation(Dependencies.AndroidxTest.extJunit)
  androidTestImplementation(Dependencies.Espresso.espressoCore)
}
