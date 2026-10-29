// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // Declare shared plugin versions here; the app module applies the plugins it needs.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

buildscript {
    dependencies {
        // Makes the secrets Gradle plugin available to the app module.
        classpath("com.google.android.libraries.mapsplatform.secrets-gradle-plugin:secrets-gradle-plugin:2.0.1")
    }
}
