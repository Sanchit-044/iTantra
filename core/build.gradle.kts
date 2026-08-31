plugins {
    kotlin("jvm") version "2.1.0"
}

kotlin {
    // Deliberately NOT jvmToolchain(17): that forces Gradle to locate or download a
    // JDK 17 even though :core is plain Kotlin and compiles fine on a newer JDK.
    // The bytecode target is pinned to 17 so :core stays consumable by the Android
    // module, which does require a 17 toolchain (see BUILD.md).
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

java {
    // Keep the (unused, but always-registered) Java compile task aligned with the
    // Kotlin target so Gradle's JVM-target validation passes.
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.3")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
    }
}
