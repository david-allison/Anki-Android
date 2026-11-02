// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.io.File

class JacocoPluginTest : JacocoTestFixture() {
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

    @TestFactory
    fun `coverage graph before and after adding and removing source`(): List<DynamicTest> {
        val before = dryRunCoverage()
        val cached = dryRunCoverage()
        writeFile("empty/custom-tests/ExampleTest.kt", "class ExampleTest")
        val added = dryRunCoverage()
        assertThat(File(projectDir, "empty/custom-tests/ExampleTest.kt").delete(), equalTo(true))
        val removed = dryRunCoverage()

        // Retain independent reported checks while the graph changes share one fixture.
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
            DynamicTest.dynamicTest("removing the last test updates the cached task graph") {
                assertThat(added, containsString(":empty:testDebugUnitTest SKIPPED"))
                assertThat(removed, not(containsString(":empty:testDebugUnitTest")))
            },
        )
    }

    private fun dryRunCoverage(): String = runCoverage("--dry-run").output

    private fun runCoverage(vararg arguments: String): BuildResult =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withTestKitDir(File("build/test-kit").absoluteFile)
            .withPluginClasspath()
            .withArguments(":app:jacocoUnitTestReport", "--configuration-cache", "--stacktrace", *arguments)
            .withTestDaemonJvm()
            .build()
}
