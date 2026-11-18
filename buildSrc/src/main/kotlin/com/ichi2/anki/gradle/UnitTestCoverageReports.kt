// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import com.android.build.api.variant.Variant
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register
import org.gradle.testing.jacoco.tasks.JacocoReport

/** The local aggregate and the independently replaceable unit-suite reports uploaded to Codecov. */
internal class UnitTestCoverageReports(
    private val app: Project,
    private val aggregate: TaskProvider<JacocoReport>,
    private val selectedModules: Set<String>?,
) {
    private val runtimeModules by lazy { app.runtimeProjectDependencies("playDebugRuntimeClasspath").toSet() + app }
    private val coverageEnabled = app.isCoverageEnabled()
    private val moduleClasses = mutableMapOf<Project, FileCollection>()
    private val ciReports =
        app.tasks.register<Sync>("jacocoCiUnitTestReports") {
            group = "verification"
            description = "Collects coverage XML for the selected unit-test suites."
            into(app.layout.buildDirectory.dir("reports/codecov"))
        }

    fun registerAndroidSuite(
        module: Project,
        variant: Variant,
        classExcludes: List<String>,
        hasTests: () -> Boolean = { true },
    ) {
        registerSuite(
            module = module,
            taskName = "test${variant.name.replaceFirstChar { it.uppercase() }}UnitTest",
            runtimeClasspath = "${variant.name}UnitTestRuntimeClasspath",
            sources = module.files(variant.sources.java?.all, variant.sources.kotlin?.all),
            classes =
                module.androidCoverageClasses(
                    report = aggregate,
                    variant = variant,
                    classExcludes = classExcludes,
                ),
            hasTests = hasTests,
        )
    }

    fun registerSuite(
        module: Project,
        taskName: String,
        runtimeClasspath: String,
        sources: FileCollection,
        classes: FileCollection,
        hasTests: () -> Boolean = { true },
    ) {
        moduleClasses[module] = classes
        if (selectedModules != null && module.path !in selectedModules) return

        aggregate.configure {
            if (module in runtimeModules) {
                classDirectories.from(classes)
                sourceDirectories.from(sources)
            }
        }
        module.includeTestExecutionData(
            report = aggregate,
            taskName = taskName,
            pattern = "**/*.exec",
            includeModule = { module in runtimeModules && hasTests() },
        )

        val flag = "unit-" + module.path.removePrefix(":").replace(':', '_')
        val xml = app.layout.buildDirectory.file("reports/jacoco/unit-suites/$flag.xml")
        val suiteReport =
            app.tasks.register<JacocoReport>("jacocoReport_$flag") {
                // AGP binds its class artifact providers when their owning task is realized.
                aggregate.get()
                val dependencies = module.runtimeProjectDependencies(runtimeClasspath)
                classDirectories.from((dependencies + module).filter { it in runtimeModules }.mapNotNull(moduleClasses::get))
                reports.xml.required.set(true)
                reports.xml.outputLocation.set(xml)
                reports.html.required.set(false)
                // A selected suite with no remaining tests must replace its previous coverage.
                val collectCoverage = coverageEnabled
                setOnlyIf { collectCoverage }
            }
        module.includeTestExecutionData(
            report = suiteReport,
            taskName = taskName,
            pattern = "**/*.exec",
            includeModule = hasTests,
        )
        ciReports.configure {
            if (module in runtimeModules) {
                dependsOn(suiteReport)
                if (coverageEnabled) from(xml)
            }
        }
    }
}
