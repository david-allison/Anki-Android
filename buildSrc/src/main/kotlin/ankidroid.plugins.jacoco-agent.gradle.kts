// SPDX-License-Identifier: GPL-3.0-or-later

import com.ichi2.anki.gradle.libsVersionFor
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension

plugins {
    jacoco
}

configure<JacocoPluginExtension> {
    toolVersion = libsVersionFor("jacoco")
}

tasks.withType<Test>().configureEach {
    val jacoco = the<JacocoTaskExtension>()
    jacoco.isIncludeNoLocationClasses = true
    // Match repository package families, including our vendored drawer-layout class.
    // Instrumenting SDKs and test frameworks adds work without contributing to our reports.
    jacoco.includes = listOf("com.ichi2.*", "dev.androidbroadcast.vbpd.*", "androidx.drawerlayout.widget.*")
    jacoco.excludes =
        listOf(
            "jdk.internal.*",
            "*Test",
            "*Test\$*",
            "*TestKt*",
            "com.ichi2.testutils.*",
            "com.ichi2.anki.libanki.testutils.*",
        )
}
