// SPDX-License-Identifier: GPL-3.0-or-later

import com.ichi2.anki.gradle.configureJacocoAgent
import com.ichi2.anki.gradle.libsVersionFor
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension

plugins {
    jacoco
}

configure<JacocoPluginExtension> {
    toolVersion = libsVersionFor("jacoco")
}

configureJacocoAgent()
