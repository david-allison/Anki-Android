// SPDX-License-Identifier: GPL-3.0-or-later

import com.android.build.api.dsl.LibraryExtension
import com.ichi2.anki.gradle.addAnkiBackendDependencies

plugins {
    id("ankidroid.android.library")
    id("ankidroid.android.compose")
    id("org.jetbrains.kotlin.plugin.parcelize")
    alias(libs.plugins.kotlin.serialization)
}

configure<LibraryExtension> {
    namespace = "com.ichi2.anki.browse"
    buildFeatures.viewBinding = true
}

dependencies {
    implementation(project(":anki-common"))
    implementation(project(":common"))
    implementation(project(":common:android"))
    implementation(project(":libanki"))
    implementation(project(":compat"))
    implementation(project(":vbpd"))

    addAnkiBackendDependencies(project)

    implementation(libs.androidx.activity)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.constraintlayout)
    // Only the search-history drawable is used by this module.
    implementation(libs.search.preference) { isTransitive = false }
    implementation(libs.google.material)
    implementation(libs.jakewharton.timber)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.junit.vintage.engine)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.hamcrest)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.cashapp.turbine)
    testImplementation(libs.ivanshafran.shared.preferences.mock)
    testRuntimeOnly(libs.junit.platform.launcher)
}
