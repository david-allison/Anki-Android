// SPDX-License-Identifier: GPL-3.0-or-later

import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    `kotlin-dsl`
    alias(libs.plugins.ktlint.gradle.plugin)
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // Needed by precompiled script plugins that apply `com.android.library`.
    // Pins AGP/KGP transitively, so call sites need `id("com.android.library")` etc. with no version.
    implementation("com.android.tools.build:gradle:${libs.versions.androidGradlePlugin.get()}")
    // Force the catalog version of KGP, otherwise it's overridden by AGP.
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    implementation("org.jetbrains.kotlin:compose-compiler-gradle-plugin:${libs.versions.kotlin.get()}")
    implementation("io.github.takahirom.roborazzi:roborazzi-gradle-plugin:${libs.versions.roborazzi.get()}")
    testImplementation(gradleTestKit())
    testImplementation(libs.hamcrest)
    testImplementation(libs.junit.jupiter)
    testImplementation("org.jacoco:org.jacoco.core:${libs.versions.jacoco.get()}")
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    // Use the first-tier JIT compiler for short-lived test workers.
    // Retain the normal 240 MiB code cache; first-tier compilation otherwise reduces it to 48 MiB.
    jvmArgs("-XX:TieredStopAtLevel=1", "-XX:ReservedCodeCacheSize=240m")
    // Schedule independent fixture methods together, including methods from the same class.
    // One JVM shares the TestKit client; each method owns a fresh @TempDir project.
    maxParallelForks = 1
    val fixtureParallelism = minOf(3, gradle.startParameter.maxWorkerCount)
    systemProperty("junit.jupiter.execution.parallel.enabled", "true")
    systemProperty("junit.jupiter.execution.parallel.mode.default", "concurrent")
    systemProperty("junit.jupiter.execution.parallel.mode.classes.default", "concurrent")
    systemProperty("junit.jupiter.execution.parallel.config.strategy", "fixed")
    systemProperty("junit.jupiter.execution.parallel.config.fixed.parallelism", fixtureParallelism)
    systemProperty("junit.jupiter.execution.parallel.config.fixed.max-pool-size", fixtureParallelism)
    // Normal fixtures use one nested worker; the single agent fixture can use a fourth slot.
    systemProperty("ankidroid.testkit.agentMaxWorkers", if (gradle.startParameter.maxWorkerCount >= 4) 2 else 1)
    // Show nested Gradle build failures from TestKit in CI logs.
    testLogging.exceptionFormat = TestExceptionFormat.FULL
    // Rerun the path guard if the app build file is moved.
    inputs.files("../AnkiDroid/build.gradle.kts")
    // The screenshot fixture uses the same Android SDK as the main build.
    inputs.file("../gradle/libs.versions.toml")
}

configure<KtlintExtension> {
    version.set(libs.versions.ktlint.get())
}
