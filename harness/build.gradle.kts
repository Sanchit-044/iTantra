plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

/**
 * Headless test harnesses for Modules B2 (STT evaluation) and B4 (round-trip).
 *
 * These are instrumented tests rather than an app: they need a real device (real
 * microphone, real speaker, real NEON-capable CPU) but no user interface, which is
 * exactly what androidTest gives. There is no Activity anywhere in this module.
 */
android {
    namespace = "in.gov.itantra.harness"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // The evaluation corpus is test-only and must never ship in the product APK.
    sourceSets["androidTest"].assets.srcDir("src/androidTest/assets")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":android"))

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation(project(":core"))
    androidTestImplementation(project(":android"))
}
