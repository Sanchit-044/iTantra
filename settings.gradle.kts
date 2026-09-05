pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
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
} else {
    logger.lifecycle("[itantra] Android SDK not found -- only :core is configured. Set ANDROID_HOME to build :android/:harness.")
}
