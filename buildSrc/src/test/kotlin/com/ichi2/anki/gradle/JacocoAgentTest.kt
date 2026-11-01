// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasItems
import org.jacoco.core.tools.ExecFileLoader
import org.junit.jupiter.api.Test
import java.io.File

class JacocoAgentTest : JacocoTestFixture() {
    @Test
    fun `unit agents retain production classes without instrumenting tests and dependencies`() {
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
            writeFile(
                "$module/src/main/java/com/ichi2/anki/coverage/TestUtilsKt.java",
                "package com.ichi2.anki.coverage; public class TestUtilsKt { public static int value() { return 13; } }",
            )
            writeFile(
                "$module/src/test/java/com/ichi2/testutils/CoverageFixture.java",
                "package com.ichi2.testutils; public class CoverageFixture { public static int value() { return 17; } }",
            )
            val vendoredCheck =
                if (module == "app") {
                    "org.junit.Assert.assertEquals(9, androidx.drawerlayout.widget.ClosableDrawerLayout.value());"
                } else {
                    ""
                }
            writeFile(
                "$module/src/test/java/com/ichi2/anki/coverage/CoverageTest.java",
                """
                package com.ichi2.anki.coverage;
                public class CoverageTest {
                    static class Nested { static int value() { return 19; } }
                    @org.junit.Test public void executesRepositoryAndDependencies() {
                        org.junit.Assert.assertEquals(7, com.ichi2.anki.coverage.$className.value());
                        org.junit.Assert.assertEquals(11, org.robolectric.coverage.Dependency.value());
                        org.junit.Assert.assertEquals(13, TestUtilsKt.value());
                        org.junit.Assert.assertEquals(17, com.ichi2.testutils.CoverageFixture.value());
                        org.junit.Assert.assertEquals(19, Nested.value());
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
                tasks.withType(Test).configureEach {
                    useJUnit()
                    jvmArgs '-XX:TieredStopAtLevel=1', '-XX:ReservedCodeCacheSize=240m'
                }
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
                .withArguments(*taskPaths.toTypedArray(), "--parallel", "--configuration-cache", "--stacktrace")
                .withTestDaemonJvm()
                .build()
        for (task in taskPaths) {
            assertThat(result.task(task)?.outcome, equalTo(TaskOutcome.SUCCESS))
        }

        for ((module, className) in modules) {
            val coverage = ExecFileLoader().apply { load(File(projectDir, "$module/build/jacoco/${taskNames.getValue(module)}.exec")) }
            val coveredNames = coverage.executionDataStore.contents.filter { it.probes.any { covered -> covered } }.map { it.name }
            assertThat(coveredNames, hasItems("com/ichi2/anki/coverage/$className"))
            assertThat(coveredNames, hasItems("com/ichi2/anki/coverage/TestUtilsKt"))
            if (module == "app") {
                assertThat(coveredNames, hasItems("androidx/drawerlayout/widget/ClosableDrawerLayout"))
            }
            assertThat(coveredNames.any { it.startsWith("org/robolectric/") }, equalTo(false))
            assertThat(coveredNames.any { it.startsWith("com/ichi2/anki/coverage/CoverageTest") }, equalTo(false))
            assertThat(coveredNames.any { it.startsWith("com/ichi2/testutils/") }, equalTo(false))
        }
    }

}
