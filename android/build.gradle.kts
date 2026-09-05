plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "in.gov.itantra.android"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // Only the ABIs the target handsets actually use. Shipping x86 variants of
            // the ONNX Runtime native libraries would inflate the APK, and every
            // megabyte here is a megabyte of bundled-model budget spent.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        // Models are bundled at install time and must never be compressed-then-extracted
        // at runtime; keeping them uncompressed lets ONNX Runtime memory-map straight out
        // of the package instead of unpacking a second copy to internal storage. That
        // halves peak disk use and matters on a 2 GB device.
        androidResources {
            noCompress += listOf("onnx", "wav", "json")
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlin.coroutines.android)

    // ONNX Runtime serves BOTH the VITS TTS decoder and the IndicWav2Vec CTC acoustic
    // model -- one inference runtime for the whole app. Vosk was dropped entirely (no
    // Tamil or Bengali model); see docs/STT-BACKEND.md.
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
}
