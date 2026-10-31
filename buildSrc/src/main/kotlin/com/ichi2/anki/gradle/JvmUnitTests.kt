// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions
import org.gradle.kotlin.dsl.register
import java.io.File
import java.io.Serializable

/** Run src/testJvm tests without Robolectric while retaining the normal aggregate test command. */
fun Project.registerJvmUnitTests(robolectricVersion: String) {
    val jupiterTest =
        Regex("(?:import\\s+|@)org\\.junit\\.jupiter\\.(?:api\\.(?:Test(?:Factory|Template)?|RepeatedTest|Nested)|params\\.ParameterizedTest)\\b")
    val misplacedTests =
        fileTree("src/test/java") { include("**/*.kt", "**/*.java") }.files
            .filter { jupiterTest.containsMatchIn(it.readText()) }
    require(misplacedTests.isEmpty()) {
        "Jupiter tests must run in src/testJvm/java: ${misplacedTests.joinToString { it.relativeTo(projectDir).path }}"
    }
    val sources = fileTree("src/testJvm/java") { include("**/*Test.kt", "**/*Test.java") }
    val patterns = sources.files.sorted().flatMap { source ->
        val text = source.readText()
        val packageName = Regex("(?m)^package\\s+([\\w.]+)").find(text)?.groupValues?.get(1).orEmpty()
        val prefix = if (packageName.isEmpty()) "" else packageName.replace('.', '/') + "/"
        val declarations = Regex("(?m)^(?:(?:public|private|internal|open|abstract|data|sealed|value|enum|final)\\s+)*(?:class|object)\\s+(\\w+)")
        val classes = declarations.findAll(text).map { it.groupValues[1] }.toList() + (source.nameWithoutExtension + "Kt")
        classes.distinct().flatMap { name -> listOf("$prefix$name.class", "$prefix$name\$*.class") }
    }
    if (patterns.isEmpty()) return
    val components = extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
    components.onVariants(components.selector().withBuildType("debug")) { variant ->
        val variantName = variant.name.replaceFirstChar(Char::uppercaseChar)
        val aggregateName = "test${variantName}UnitTest"
        val nativeName = "test${variantName}JvmUnitTest"
        registerJvmUnitTestTask(aggregateName, nativeName, patterns, robolectricVersion, androidTestsUseJUnit4 = true)
    }
}

/** Preserves aggregate and focused execution when native tests have their own worker JVMs. */
@JvmOverloads
fun Project.registerJvmUnitTestTask(
    aggregateName: String,
    nativeName: String,
    patterns: List<String>,
    robolectricVersion: String,
    androidTestsUseJUnit4: Boolean = false,
) {
    val nativeCount = layout.buildDirectory.file("test-results/$nativeName.count").get().asFile
    val aggregateCount = layout.buildDirectory.file("test-results/$aggregateName.count").get().asFile
    val nativeTest = tasks.register<Test>(nativeName) {
        val native = this
        val aggregate = tasks.named(aggregateName, Test::class.java).get()
        val aggregatePatterns = (aggregate.filter as DefaultTestFilter).commandLineIncludePatterns.toList()
        native.group = "verification"
        native.description = "Runs ${aggregateName} JVM tests without Robolectric."
        native.testClassesDirs = aggregate.testClassesDirs
        native.classpath = aggregate.classpath.filter { file -> !isRobolectricRuntime(file, robolectricVersion) }
        native.include(patterns)
        native.javaLauncher.set(aggregate.javaLauncher)
        native.maxParallelForks = aggregate.maxParallelForks
        native.minHeapSize = aggregate.minHeapSize
        native.maxHeapSize = aggregate.maxHeapSize
        native.setJvmArgs(aggregate.jvmArgs)
        native.systemProperties = aggregate.systemProperties.filterKeys { !it.startsWith("robolectric") }
        native.environment(aggregate.environment)
        val original = aggregate.options as JUnitPlatformOptions
        native.useJUnitPlatform {
            val options = this
            options.includeTags(*original.includeTags.toTypedArray())
            options.excludeTags(*original.excludeTags.toTypedArray())
            options.includeEngines(*original.includeEngines.toTypedArray())
            options.excludeEngines(*original.excludeEngines.toTypedArray())
        }
        if (androidTestsUseJUnit4) {
            // Robolectric uses JUnit 4. Its detector rejects helpers before forkEvery counts them.
            aggregate.useJUnit {
                includeCategories(*original.includeTags.toTypedArray())
                excludeCategories(*original.excludeTags.toTypedArray())
            }
            // Only Android test classes remain here; reuse their SDK sandboxes across more classes.
            aggregate.forkEvery = 80
        }
        native.filter.isFailOnNoMatchingTests = false
        native.failOnNoDiscoveredTests.set(false)
        native.reports.junitXml.outputLocation.set(layout.buildDirectory.dir("test-results/$nativeName"))
        native.reports.html.outputLocation.set(layout.buildDirectory.dir("reports/tests/$nativeName"))
        native.binaryResultsDirectory.set(layout.buildDirectory.dir("test-results/$nativeName/binary"))
        native.inputs.property("aggregateTestFilters", aggregatePatterns)
        native.outputs.file(nativeCount)
        native.addTestListener(TestCountListener(nativeCount))
        native.doFirst {
            nativeCount.parentFile.mkdirs()
            nativeCount.writeText("0")
            val currentFilter = (this as Test).filter as DefaultTestFilter
            if (aggregatePatterns.isNotEmpty() && currentFilter.commandLineIncludePatterns.isEmpty()) {
                currentFilter.setIncludePatterns(*aggregatePatterns.toTypedArray())
            }
        }
        native.doLast {
            val patterns = ((this as Test).filter as DefaultTestFilter).commandLineIncludePatterns
            if (patterns.isNotEmpty() && nativeCount.readTestCount() == 0L) {
                throw GradleException("No JVM tests matched $patterns")
            }
        }
    }
    tasks.withType(Test::class.java).configureEach {
        val aggregate = this
        if (aggregate.name != aggregateName) return@configureEach
        aggregate.exclude(patterns)
        aggregate.dependsOn(nativeTest)
        aggregate.filter.isFailOnNoMatchingTests = false
        aggregate.failOnNoDiscoveredTests.set(false)
        aggregate.outputs.file(aggregateCount)
        aggregate.addTestListener(TestCountListener(aggregateCount))
        aggregate.doFirst {
            aggregateCount.parentFile.mkdirs()
            aggregateCount.writeText("0")
        }
        aggregate.doLast {
            val patterns = ((this as Test).filter as DefaultTestFilter).commandLineIncludePatterns
            if (patterns.isNotEmpty() && aggregateCount.readTestCount() + nativeCount.readTestCount() == 0L) {
                throw GradleException("No tests matched $patterns")
            }
        }
    }
}

private fun File.readTestCount(): Long = if (isFile) readText().trim().toLong() else 0L

private fun isRobolectricRuntime(file: File, version: String): Boolean {
    if (file.invariantSeparatorsPath.contains("/org.robolectric/")) return true
    val artifact = file.name.removePrefix("jetified-")
    if (artifact.startsWith("nativeruntime-")) return true
    val names = listOf("robolectric", "shadows-framework", "resources", "plugins-maven-dependency-resolver", "junit", "sandbox", "utils-reflector", "shadowapi", "utils", "pluginapi", "annotations")
    return names.any { artifact == "$it-$version.jar" }
}

private class TestCountListener(private val output: File) : TestListener, Serializable {
    override fun beforeSuite(suite: TestDescriptor) = Unit
    override fun beforeTest(testDescriptor: TestDescriptor) = Unit
    override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) = Unit
    override fun afterSuite(suite: TestDescriptor, result: TestResult) {
        if (suite.parent == null) {
            output.parentFile.mkdirs()
            output.writeText(result.testCount.toString())
        }
    }
}
