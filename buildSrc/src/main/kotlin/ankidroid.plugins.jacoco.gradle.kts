// SPDX-License-Identifier: GPL-3.0-or-later

import com.ichi2.anki.gradle.assertNonzeroAndroidTestCoverage
import com.ichi2.anki.gradle.registerJacocoCoverageReports
import java.util.Properties

plugins {
    id("ankidroid.plugins.jacoco-agent")
}

fun getLocalProperties(): Properties {
    val propertiesFile = project.rootProject.file("local.properties")

    val properties = Properties()

    if (propertiesFile.exists()) {
        properties.load(propertiesFile.inputStream())
    }

    return properties
}

val openReportRequested = project.hasProperty("open-report")
val linuxHtmlCmd: String? = getLocalProperties()["linux-html-cmd"]?.toString()

// source: https://medium.com/jamf-engineering/android-kotlin-code-coverage-with-jacoco-sonar-and-gradle-plugin-6-x-3933ed503a6e
val fileFilter =
    listOf(
        // android
        "**/R.class",
        "**/R\$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/**/*.*",
        // kotlin
        "**/*MapperImpl*.*",
        "**/*\$ViewInjector*.*",
        "**/*\$ViewBinder*.*",
        "**/BuildConfig.*",
        "**/*Component*.*",
        "**/*BR*.*",
        "**/Manifest*.*",
        "**/*\$Lambda\$*.*",
        "**/*Companion*.*",
        "**/*Module*.*",
        "**/*Dagger*.*",
        "**/*Hilt*.*",
        "**/*MembersInjector*.*",
        "**/*_MembersInjector.class",
        "**/*_Factory*.*",
        "**/*_Provide*Factory*.*",
        "**/*Extensions*.*",
        // sealed and data classes
        "**/*\$Result.*",
        "**/*\$Result\$*.*",
    )

registerJacocoCoverageReports(
    fileFilter,
    openReportRequested,
    linuxHtmlCmd,
)

// Issue 16640 - some emulators run, but register zero coverage
tasks.register("assertNonzeroAndroidTestCoverage") {
    // Resolve the file at configuration time, which is Gradle Configuration Cache compatible
    val jacocoReportFile = layout.buildDirectory.file("reports/jacoco/jacocoAndroidTestReport/jacocoAndroidTestReport.xml")
    doLast {
        val jacocoReport = jacocoReportFile.get().asFile

        assertNonzeroAndroidTestCoverage(jacocoReport, logger)
    }
}

afterEvaluate {
    tasks.named("jacocoAndroidTestReport").configure {
        finalizedBy("assertNonzeroAndroidTestCoverage")
    }
}
