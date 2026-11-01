// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** Test of `ankidroid.plugins.screenshots.gradle.kts` */
class ScreenshotPluginTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `normal unit tests never construct excluded screenshot runners`() {
        writeFixture()
        writeFile(
            "src/test/java/com/ichi2/anki/ScreenshotTestCategory.java",
            "package com.ichi2.anki; public interface ScreenshotTestCategory {}",
        )
        writeFile(
            "src/test/java/regression/NormalTest.java",
            """
            package regression;
            public class NormalTest {
                @org.junit.jupiter.api.Test void normal() {}
            }
            """.trimIndent(),
        )
        writeFile(
            "src/test/java/regression/DiscoveryGuardScreenshotTest.java",
            """
            package regression;
            import java.nio.file.Files;
            import java.nio.file.Path;
            import org.junit.experimental.categories.Category;
            import org.junit.runner.Description;
            import org.junit.runner.Runner;
            import org.junit.runner.RunWith;
            import org.junit.runner.notification.RunNotifier;

            @Category(com.ichi2.anki.ScreenshotTestCategory.class)
            @RunWith(DiscoveryGuardScreenshotTest.DiscoveryGuard.class)
            public class DiscoveryGuardScreenshotTest {
                public static class DiscoveryGuard extends Runner {
                    private final Class<?> type;
                    public DiscoveryGuard(Class<?> type) throws Exception {
                        this.type = type;
                        Files.writeString(Path.of("screenshot-runner-created.txt"), "discovered");
                    }
                    public Description getDescription() { return Description.createSuiteDescription(type); }
                    public void run(RunNotifier notifier) {}
                }
            }
            """.trimIndent(),
        )
        writeFile("changed.txt", "false")

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withTestKitDir(File("build/test-kit").absoluteFile)
            .withPluginClasspath()
            .withArguments("testDebugUnitTest", "--configuration-cache", "--stacktrace")
            .withTestDaemonJvm()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":testDebugUnitTest")?.outcome, result.output)
        assertFalse(File(projectDir, "screenshot-runner-created.txt").exists(), result.output)
    }

    @Test
    fun `matching screenshots clear previous diffs including cached results`() {
        writeFixture()
        // Preserve baselines not exercised by this run, even inside a directory named 'diffs'.
        // Populate that directory up front so creating it cannot change the comparison's cache key.
        val baselines = mapOf("Example/screen.png" to "baseline", "Example/diffs/other.png" to "unselected baseline")
        val diffs = mapOf("Example/diffs/screen_compare.png" to "comparison", "Example/diffs/screen_actual.png" to "actual")
        baselines.forEach { (path, contents) ->
            writeFile("build/outputs/roborazzi/$path", contents)
        }

        val runner =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withTestKitDir(File("build/test-kit").absoluteFile)
                .withPluginClasspath()
                .withArguments("compareScreenshots", "-Pscreenshot", "--build-cache", "--configuration-cache", "--stacktrace")
                .withTestDaemonJvm()

        // Run changed → matching once normally, then repeat with both results restored from cache.
        listOf(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE).forEach { outcome ->
            listOf(true, false).forEach { changed ->
                writeFile("changed.txt", changed.toString())
                val result = runner.build()
                assertEquals(outcome, result.task(":testDebugUnitTest")?.outcome, result.output)
                val expectedDiffs = if (changed) diffs else emptyMap()
                listOf("outputs/roborazzi", "intermediates/roborazzi").forEach { directory ->
                    assertEquals(baselines + expectedDiffs, fileContents(directory), "$directory: $outcome, changed=$changed")
                }
                if (outcome == TaskOutcome.FROM_CACHE) {
                    assertTrue(result.output.contains("Reusing configuration cache."))
                    assertEquals(expectedDiffs.filterKeys { it.endsWith("_compare.png") }, fileContents("reports/screenshot-diffs"))
                }
            }
        }
    }

    private fun fileContents(directory: String): Map<String, String> {
        val root = File(projectDir, "build/$directory")
        return root
            .walkTopDown()
            .filter { it.isFile }
            .associate { it.relativeTo(root).invariantSeparatorsPath to it.readText() }
    }

    private fun writeFixture() {
        File("../gradle/libs.versions.toml").copyTo(File(projectDir, "libs.versions.toml"))
        File("../local.properties").takeIf { it.isFile }?.copyTo(File(projectDir, "local.properties"))
        writeFile(
            "settings.gradle",
            """
            rootProject.name = 'screenshot-cleanup-regression'
            dependencyResolutionManagement {
                repositories { google(); mavenCentral() }
                versionCatalogs { libs { from(files('libs.versions.toml')) } }
            }
            buildCache { local { directory = file('build-cache') } }
            """.trimIndent(),
        )
        writeFile("gradle.properties", "android.builtInKotlin=false\nandroid.newDsl=false\norg.gradle.workers.max=2\n")

        writeFile(
            "build.gradle",
            """
            plugins {
                id 'com.android.library'
                id 'ankidroid.plugins.screenshots'
            }
            android {
                namespace = 'regression.screenshots'
                compileSdk = libs.versions.compileSdk.get().toInteger()
                compileSdkMinor = libs.versions.compileSdkMinor.get().toInteger()
                defaultConfig { minSdk = 24 }
            }
            dependencies {
                testImplementation libs.junit.jupiter
                testImplementation libs.junit.vintage.engine
                testRuntimeOnly libs.junit.platform.launcher
            }
            tasks.withType(Test).configureEach {
                jvmArgs '-XX:TieredStopAtLevel=1', '-XX:ReservedCodeCacheSize=240m'
                inputs.file('changed.txt')
            }
            """.trimIndent(),
        )
        // Pixel comparison is outside this test's scope. Emit a renderer's diff files and let
        // the real Roborazzi and screenshot plugins handle copying, cleanup and cache restoration.
        writeFile(
            "src/test/java/regression/ScreenshotTest.java",
            """
            package regression;

            import java.nio.file.Files;
            import java.nio.file.Path;
            import org.junit.jupiter.api.Tag;
            import org.junit.jupiter.api.Test;

            @Tag("com.ichi2.anki.ScreenshotTestCategory")
            public class ScreenshotTest {
                @Test
                void screenshot() throws Exception {
                    if (Files.readString(Path.of("changed.txt")).equals("true")) {
                        Path diffs = Path.of("build/outputs/roborazzi/Example/diffs");
                        Files.createDirectories(diffs);
                        Files.writeString(diffs.resolve("screen_compare.png"), "comparison");
                        Files.writeString(diffs.resolve("screen_actual.png"), "actual");
                    }
                }
            }
            """.trimIndent(),
        )
    }

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
