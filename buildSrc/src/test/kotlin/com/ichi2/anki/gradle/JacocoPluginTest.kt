// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.not
import org.jacoco.core.tools.ExecFileLoader
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathFactory

class JacocoPluginTest : JacocoTestFixture() {
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

    @TestFactory
    fun `coverage graph before and after adding source`(): List<DynamicTest> {
        val before = dryRunCoverage()
        val cached = dryRunCoverage()
        writeFile("empty/custom-tests/ExampleTest.kt", "class ExampleTest")
        val added = dryRunCoverage()

        // Retain independent reported checks while sharing their identical initial graph.
        return listOf(
            DynamicTest.dynamicTest("report covers runtime modules and skips empty library test tasks") {
                for (module in listOf("app", "library", "nested/core", "empty")) {
                    assertThat(before, containsString("$module/src/main/kotlin"))
                }
                assertThat(before, not(containsString("tooling/src/main")))
                assertThat(before, containsString(":app:testPlayDebugUnitTest SKIPPED"))
                assertThat(before, containsString(":library:testDebugUnitTest SKIPPED"))
                assertThat(before, containsString(":nested:core:test SKIPPED"))
                assertThat(before, not(containsString(":empty:testDebugUnitTest")))
                assertThat(before, not(containsString(":tooling:test")))
            },
            DynamicTest.dynamicTest("adding the first test in a custom source root updates the cached task graph") {
                assertThat(before, not(containsString(":empty:testDebugUnitTest")))
                assertThat(cached, containsString("Reusing configuration cache."))
                assertThat(added, containsString(":empty:testDebugUnitTest SKIPPED"))
            },
        )
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
            .withTestDaemonJvm()
            .build()
}
