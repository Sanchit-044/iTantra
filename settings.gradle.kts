pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "itantra"

// :core is pure Kotlin/JVM and always builds -- no Android SDK required.
include(":core")

// :android and :harness need the Android SDK. They are included only when an SDK
// is actually present, so `./gradlew :core:test` works on a bare machine (CI, laptop
// without Studio). See BUILD.md.
val androidSdkPresent: Boolean =
    System.getenv("ANDROID_HOME") != null ||
        System.getenv("ANDROID_SDK_ROOT") != null ||
        file("local.properties").takeIf { it.exists() }
            ?.readText()?.contains("sdk.dir") == true

if (androidSdkPresent) {
    include(":app")
    include(":android")
    include(":harness")
    // Install-time asset pack holding the bundled models. The future :app module must
    // declare `assetPacks += listOf(":models-pack")`; see docs/BUILD.md.
    include(":models-pack")
} else {
    logger.lifecycle("[itantra] Android SDK not found -- only :core is configured. Set ANDROID_HOME to build :android/:harness.")
}
