import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// Release signing is configured from keystore.properties in the project root.
// That file (and the .jks it points to) are never committed - see .gitignore.
// When it is absent (e.g. a fresh clone, or a debug-only build) the release
// signingConfig stays unset and `assembleRelease` produces an unsigned APK.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) FileInputStream(keystorePropertiesFile).use { load(it) }
}

android {
    namespace = "com.notipeek"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.notipeek"
        minSdk = 26          // Android 8.0 - MessagingStyle + reliable NotificationListenerService
        targetSdk = 35       // Android 15 - required target level for new Google Play submissions
        versionCode = 4
        versionName = "0.1.3"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                // Resolve relative to the project root (where keystore.properties
                // lives), not the app module directory.
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Kept off for the first public release so the shipped build behaves
            // identically to the device-verified debug build (no R8 surprises).
            // Can be enabled later as a separate, tested optimisation.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                null
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }  // matches Kotlin 1.9.24
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.activity:activity-compose:1.9.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Room - local message history
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
}
