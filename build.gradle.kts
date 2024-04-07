// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
  dependencies {
    classpath(Plugins.androidGradlePlugin)
    classpath(Plugins.kotlinGradlePlugin)
    classpath(Plugins.navSafeArgsGradlePlugin)
  }
}

allprojects {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
  configureSpotless()
}

subprojects {
  // We have some empty folders like the :contrib root folder, which Gradle recognizes as projects.
  // Don't configure plugins for those folders.
  if (project.name.equals("sensing")) {
    configureLicensee()
  }
}
