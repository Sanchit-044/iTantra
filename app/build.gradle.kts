plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "in.gov.itantra"
    compileSdk = 35

    defaultConfig {
        applicationId = "in.gov.itantra"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-prototype"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Restrict language resources to supported languages to save APK size
        resourceConfigurations.addAll(listOf("en", "hi", "ta", "bn", "gu", "mr", "kn", "ml", "te", "or"))

        ndk {
            // Drop x86/x86_64 to save tens of MBs from ONNX native libs
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        // Where LocalLanguagePackManager downloads the nine non-bundled language packs
        // from (see model-host/README.md at the repo root) -- empty means "bundled
        // Hindi only, no network fetch," which is the safe default for a clean clone.
        // Override per machine/demo with -PmodelPackBaseUrl=http://<lan-ip>:8000
        // (e.g. `python -m http.server 8000` run from model-host/) rather than editing
        // this file, so nobody accidentally commits a demo laptop's LAN IP.
        val modelPackBaseUrl = (project.findProperty("modelPackBaseUrl") as? String).orEmpty()
        buildConfigField("String", "MODEL_PACK_BASE_URL", "\"$modelPackBaseUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }


}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":android"))
    implementation(libs.androidx.benchmark.common)
    // assetPacks is defined inside the android { ... } block below

    implementation(libs.kotlin.coroutines.core)
    implementation(libs.kotlin.coroutines.android)

    // Compose
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    debugImplementation(libs.compose.ui.tooling)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
}
