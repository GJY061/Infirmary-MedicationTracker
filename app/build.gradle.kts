import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // KSP generates Room's database and DAO implementations from the annotated Kotlin sources.
    alias(libs.plugins.ksp)
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
}

// Read machine-local configuration at build time; an absent key becomes an empty string.
secrets {
    defaultPropertiesFileName = "secrets.defaults.properties"
    // This key is escaped and emitted explicitly by buildConfigField below.
    ignoreList.add("GEMINI_API_KEY")
}

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { input -> load(input) }
    }
}
val geminiApiKey = localProperties.getProperty("GEMINI_API_KEY", "")
// Escape the value for the Java string literal emitted into the generated BuildConfig class.
val escapedGeminiApiKey = geminiApiKey.replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "com.example.infirmary"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.infirmary"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        // The repository reads this constant. Changing local.properties requires rebuilding the app.
        // Keeping the file out of Git does not hide the embedded value inside the compiled APK.
        buildConfigField("String", "GEMINI_API_KEY", "\"$escapedGeminiApiKey\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        // Enable Compose UI compilation and generation of the custom BuildConfig field above.
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Android integration and Compose UI. The BOM aligns versions of Compose libraries.
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    // HTTP interfaces and JSON mapping; the repository selects GsonConverterFactory at runtime.
    implementation(libs.gson)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    // ViewModels own screen state; Room provides local storage and Flow/suspend DAO support.
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    // Host/device test dependencies and debug-only Compose tooling.
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
