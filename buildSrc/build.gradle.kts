import org.gradle.kotlin.dsl.`kotlin-dsl`

plugins {
  `kotlin-dsl`
}

repositories {
  google()
  gradlePluginPortal()
  mavenCentral()
}

dependencies {
  implementation("com.diffplug.spotless:spotless-plugin-gradle:6.6.0")

  implementation("com.android.tools.build:gradle:8.1.4")

  implementation("app.cash.licensee:licensee-gradle-plugin:1.8.0")
}