plugins {
  id(Plugins.BuildPlugins.application)
  id(Plugins.BuildPlugins.kotlinAndroid)
  id(Plugins.BuildPlugins.navSafeArgs)
}

kotlin { jvmToolchain(17) }

android {
  namespace = "com.google.android.sensing.hear"
  compileSdk = Sdk.compileSdk

  defaultConfig {
    applicationId = "com.google.android.sensing.hear"
    minSdk = Sdk.minSdk
    targetSdk = Sdk.targetSdk
    versionCode = 1
    versionName = "1.0"
    testInstrumentationRunner = Dependencies.androidJunitRunner
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      // proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),
      // "proguard-rules.pro")
    }
  }

  buildFeatures {
    buildConfig = true
    viewBinding = true
  }

  packaging { resources.excludes.addAll(listOf("*/META-INF/*")) }
  sourceSets { getByName("main") { java { srcDirs("src/main/java", "src/main/java/proto") } } }
}

dependencies {
  implementation(Dependencies.Androidx.coreKtx)
  implementation(Dependencies.Androidx.appCompat)
  implementation(Dependencies.Androidx.fragmentKtx)
  implementation(Dependencies.material)
  implementation(Dependencies.gson)
  implementation(Dependencies.timber)
  implementation(Dependencies.Navigation.navFragmentKtx)
  implementation("androidx.navigation:navigation-ui-ktx:2.6.0")

  implementation("com.google.cloud:google-cloud-aiplatform:3.35.0")
  implementation("io.grpc:grpc-okhttp:1.61.0")

  testImplementation(Dependencies.junit)
  androidTestImplementation(Dependencies.AndroidxTest.extJunit)
  androidTestImplementation(Dependencies.Espresso.espressoCore)

  implementation(project(":sensing"))
}
