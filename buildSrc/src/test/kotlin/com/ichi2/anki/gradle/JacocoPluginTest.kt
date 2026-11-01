// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.hasItems
import org.hamcrest.Matchers.not
import org.jacoco.core.tools.ExecFileLoader
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathFactory

class JacocoPluginTest {
    @TempDir
    lateinit var projectDir: File

    @BeforeEach
    fun setUp() {
        val fixture = File(requireNotNull(javaClass.getResource("/jacoco-project")).toURI())
        fixture.walkTopDown().filter { it.isFile && it.extension == "fixture" }.forEach { source ->
            writeFile(source.relativeTo(fixture).path.removeSuffix(".fixture"), source.readText())
        }
        for (module in listOf("app", "library", "nested/core", "empty", "tooling")) {
            File(projectDir, module).mkdirs()
        }
        File("../gradle/libs.versions.toml").copyTo(File(projectDir, "libs.versions.toml"))
        val localProperties = Properties()
        File("../local.properties").takeIf { it.isFile }?.inputStream()?.use { localProperties.load(it) }
        // Preserve SDK settings without inheriting the checkout's coverage preference.
        localProperties.remove("enable_coverage")
        File(projectDir, "local.properties").outputStream().use { localProperties.store(it, null) }
        writeFile("gradle.properties", "org.gradle.workers.max=2\n")
        writeFile("library/src/test/java/ExampleTest.java", "class ExampleTest {}")
        writeFile("nested/core/src/test/kotlin/ExampleTest.kt", "class ExampleTest")
        // Resources alone must not cause a unit-test task to be scheduled.
        writeFile("empty/src/test/resources/example.txt", "Not a test source")
    }

    @ParameterizedTest
    @CsvSource("false, false", "true, true")
    fun `coverage flag controls app and library unit test agents`(
        property: String,
        enabled: Boolean,
    ) {
        assertUnitTestCoverage(enabled, "-Penable_coverage=$property")
    }

    @Test
    fun `unit test coverage is enabled by default`() {
        assertUnitTestCoverage(true)
    }

    @Test
    fun `local properties can disable unit test coverage`() {
        File(projectDir, "local.properties").appendText("\nenable_coverage=false\n")
        assertUnitTestCoverage(false)
    }

    @Test
    fun `Gradle property overrides local coverage preference`() {
        File(projectDir, "local.properties").appendText("\nenable_coverage=false\n")
        assertUnitTestCoverage(true, "-Penable_coverage=true")
    }

    @Test
    fun `changing coverage preference invalidates configuration cache`() {
        File(projectDir, "gradle.properties").appendText("enable_coverage=false\n")
        assertUnitTestCoverage(false)
        val cached = assertUnitTestCoverage(false)
        assertThat(cached.output, containsString("Reusing configuration cache."))

        File(projectDir, "gradle.properties").appendText("enable_coverage=true\n")
        assertUnitTestCoverage(true)
    }

    @Test
    fun `unit agents retain repository and vendored classes without instrumenting dependencies`() {
        val modules = mapOf("app" to "CoveredApp", "library" to "CoveredLibrary", "nested/core" to "CoveredCore")
        for ((module, className) in modules) {
            writeFile(
                "$module/src/main/java/com/ichi2/anki/coverage/$className.java",
                "package com.ichi2.anki.coverage; public class $className { public static int value() { return 7; } }",
            )
            writeFile(
                "$module/src/test/java/org/robolectric/coverage/Dependency.java",
                "package org.robolectric.coverage; public class Dependency { public static int value() { return 11; } }",
            )
            val vendoredCheck =
                if (module == "app") {
                    "org.junit.Assert.assertEquals(9, androidx.drawerlayout.widget.ClosableDrawerLayout.value());"
                } else {
                    ""
                }
            writeFile(
                "$module/src/test/java/regression/CoverageTest.java",
                """
                package regression;
                public class CoverageTest {
                    @org.junit.Test public void executesRepositoryAndDependencies() {
                        org.junit.Assert.assertEquals(7, com.ichi2.anki.coverage.$className.value());
                        org.junit.Assert.assertEquals(11, org.robolectric.coverage.Dependency.value());
                        $vendoredCheck
                    }
                }
                """.trimIndent(),
            )
        }
        writeFile(
            "app/src/main/java/androidx/drawerlayout/widget/ClosableDrawerLayout.java",
            "package androidx.drawerlayout.widget; public class ClosableDrawerLayout { public static int value() { return 9; } }",
        )
        for (module in modules.keys) {
            val agentPlugin = if (module == "app") "" else "apply plugin: 'ankidroid.plugins.jacoco-agent'"
            File(projectDir, "$module/build.gradle").appendText(
                """

                $agentPlugin
                dependencies { testImplementation libs.junit.vintage.engine }
                tasks.withType(Test).configureEach { useJUnit() }
                """.trimIndent(),
            )
        }

        val taskNames = mapOf("app" to "testPlayDebugUnitTest", "library" to "testDebugUnitTest", "nested/core" to "test")
        val taskPaths = taskNames.map { (module, task) -> ":${module.replace('/', ':')}:$task" }
        val result =
            GradleRunner.create()
                .withProjectDir(projectDir)
                .withTestKitDir(File("build/test-kit").absoluteFile)
                .withPluginClasspath()
                .withArguments(*taskPaths.toTypedArray(), "--configuration-cache", "--stacktrace")
                .build()
        for (task in taskPaths) {
            assertThat(result.task(task)?.outcome, equalTo(TaskOutcome.SUCCESS))
        }

        for ((module, className) in modules) {
            val coverage = ExecFileLoader().apply { load(File(projectDir, "$module/build/jacoco/${taskNames.getValue(module)}.exec")) }
            val coveredNames = coverage.executionDataStore.contents.filter { it.probes.any { covered -> covered } }.map { it.name }
            assertThat(coveredNames, hasItems("com/ichi2/anki/coverage/$className"))
            if (module == "app") {
                assertThat(coveredNames, hasItems("androidx/drawerlayout/widget/ClosableDrawerLayout"))
            }
            assertThat(coveredNames.any { it.startsWith("org/robolectric/") }, equalTo(false))
        }
    }

    @Test
    fun `report covers runtime modules and skips empty library test tasks`() {
        val output = dryRunCoverage()

        for (module in listOf("app", "library", "nested/core", "empty")) {
            assertThat(output, containsString("$module/src/main/kotlin"))
        }
        assertThat(output, not(containsString("tooling/src/main")))

        assertThat(output, containsString(":app:testPlayDebugUnitTest SKIPPED"))
        assertThat(output, containsString(":library:testDebugUnitTest SKIPPED"))
        assertThat(output, containsString(":nested:core:test SKIPPED"))
        assertThat(output, not(containsString(":empty:testDebugUnitTest")))
        assertThat(output, not(containsString(":tooling:test")))
    }

    @Test
    fun `coverage reports do not realize unrelated tasks`() {
        // AGP 9.5 registers Compose Preview tasks that fail during configuration
        // for variants without unit tests. Coverage must leave those tasks lazy.
        for (module in listOf("app", "library")) {
            File(projectDir, "$module/build.gradle").appendText(
                """

                tasks.register('unrelatedPreviewTask') {
                    throw new GradleException('Coverage must not realize unrelated tasks')
                }
                """.trimIndent(),
            )
        }

        val output = runCoverage(":app:jacocoAndroidTestReport", "--dry-run").output
        assertThat(output, containsString(":app:jacocoUnitTestReport SKIPPED"))
        assertThat(output, containsString(":app:jacocoAndroidTestReport SKIPPED"))
    }

    @Test
    fun `library without unit tests is included as uncovered in the report`() {
        // A valid, empty execution file represents a run that never touched this class.
        ExecFileLoader().save(File(projectDir, "app/build/jacoco/testPlayDebugUnitTest.exec"), false)

        // Run the real report with Android compilation, skipping only test execution.
        val result =
            runCoverage(
                "-x",
                ":app:testPlayDebugUnitTest",
                "-x",
                ":library:testDebugUnitTest",
                "-x",
                ":nested:core:test",
            )
        assertThat(result.task(":app:jacocoUnitTestReport")?.outcome, equalTo(TaskOutcome.SUCCESS))

        val report =
            DocumentBuilderFactory
                .newInstance()
                .apply { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
                .newDocumentBuilder()
                .parse(File(projectDir, "app/build/reports/jacoco/jacocoUnitTestReport/jacocoUnitTestReport.xml"))
        val xpath = XPathFactory.newInstance().newXPath()
        val counter = "/report/package/class[@name='regression/Uncovered']/counter[@type='LINE']"
        assertThat(xpath.evaluate("$counter/@covered", report), equalTo("0"))
        assertThat(xpath.evaluate("$counter/@missed", report).toInt(), greaterThan(0))
    }

    @Test
    fun `adding the first test in a custom source root updates the cached task graph`() {
        val before = dryRunCoverage()
        assertThat(before, not(containsString(":empty:testDebugUnitTest")))

        val cached = dryRunCoverage()
        assertThat(cached, containsString("Reusing configuration cache."))

        writeFile("empty/custom-tests/ExampleTest.kt", "class ExampleTest")
        val after = dryRunCoverage()
        assertThat(after, containsString(":empty:testDebugUnitTest SKIPPED"))
    }

    @Test
    fun `removing the last test updates the cached task graph`() {
        writeFile("empty/custom-tests/ExampleTest.kt", "class ExampleTest")
        val before = dryRunCoverage()
        assertThat(before, containsString(":empty:testDebugUnitTest SKIPPED"))

        assertThat(File(projectDir, "empty/custom-tests/ExampleTest.kt").delete(), equalTo(true))
        val after = dryRunCoverage()
        assertThat(after, not(containsString(":empty:testDebugUnitTest")))
    }

    private fun dryRunCoverage(): String = runCoverage("--dry-run").output

    private fun assertUnitTestCoverage(
        enabled: Boolean,
        vararg arguments: String,
    ): BuildResult {
        val result = runGradle(":app:inspectUnitTestCoverage", *arguments)
        for (task in listOf(":app:testPlayDebugUnitTest", ":library:testDebugUnitTest", ":nested:core:test")) {
            assertThat(result.output, containsString("$task coverage=$enabled"))
        }
        return result
    }

    private fun runCoverage(vararg arguments: String): BuildResult = runGradle(":app:jacocoUnitTestReport", *arguments)

    private fun runGradle(vararg arguments: String): BuildResult =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withTestKitDir(File("build/test-kit").absoluteFile)
            .withPluginClasspath()
            .withArguments("--configuration-cache", "--stacktrace", *arguments)
            .build()

    private fun writeFile(
        path: String,
        contents: String,
    ) {
        File(projectDir, path).apply {
            parentFile.mkdirs()
            writeText(contents)
        }
    }
}
