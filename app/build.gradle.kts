import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing material is never committed. Provide it either through a git-ignored
// keystore.properties in the repository root or through FLIPSCAN_* environment variables.
// Without it, release builds stay unsigned and every task still works, which is the
// default state of this repository.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(property: String, env: String): String? =
    (keystoreProperties.getProperty(property) ?: System.getenv(env))?.takeIf { it.isNotBlank() }

val signingStoreFile = signingValue("storeFile", "FLIPSCAN_STORE_FILE")
val signingStorePassword = signingValue("storePassword", "FLIPSCAN_STORE_PASSWORD")
val signingKeyAlias = signingValue("keyAlias", "FLIPSCAN_KEY_ALIAS")
val signingKeyPassword = signingValue("keyPassword", "FLIPSCAN_KEY_PASSWORD")
val hasReleaseSigning = signingStoreFile != null && signingStorePassword != null &&
    signingKeyAlias != null && signingKeyPassword != null

android {
    namespace = "app.flipscan"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.flipscan"
        minSdk = 24
        targetSdk = 36
        versionCode = 9
        versionName = "0.8.0"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(signingStoreFile!!)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            // Keep x86_64 in debug so the app still runs in Android Studio's emulator.
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Real phones are ARM; x86_64 builds are only useful for emulators.
            // The App Bundle still ships only what each device needs.
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
            // Only applied when keystore.properties or the FLIPSCAN_* variables are present.
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    lint {
        // Release builds ship ARM only on purpose: x86_64 is a debug/emulator ABI here, and the
        // app bundle splits native libraries per ABI anyway.
        disable += "ChromeOsAbiSupport"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }

    packaging {
        resources.excludes += setOf(
            "DebugProbesKt.bin",
            "META-INF/*.version"
        )
    }

    dependenciesInfo {
        // Removes the dependency metadata blob from the APK/AAB; nothing reads it at runtime.
        includeInApk = false
        includeInBundle = false
    }

    bundle {
        abi { enableSplit = true }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    // Jetpack Compose (versions managed by the BOM)
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    // CameraX. 1.4+ is the first line whose native helpers are aligned for 16 KB memory pages,
    // which Google Play requires for apps targeting API 35+ that ship native code.
    val camerax = "1.7.0"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    // System BiometricPrompt with device-credential fallback (no custom PIN, no network).
    implementation("androidx.biometric:biometric:1.1.0")

    // On-device text recognition for Digital Pins. This is the bundled artifact: the Latin OCR
    // model ships inside the APK, so no Play Services download and no network are involved.
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // OpenCV for Android (detection + perspective correction), from Maven Central.
    // 4.12.0 is the first 4.x release here whose native libraries are 16 KB page aligned.
    implementation("org.opencv:opencv:4.12.0")
}
