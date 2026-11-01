// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File

abstract class JacocoTestFixture {
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
        File("../local.properties").takeIf { it.isFile }?.copyTo(File(projectDir, "local.properties"))
        writeFile("gradle.properties", "org.gradle.workers.max=2\n")
        writeFile("library/src/test/java/ExampleTest.java", "class ExampleTest {}")
        writeFile("nested/core/src/test/kotlin/ExampleTest.kt", "class ExampleTest")
        // Resources alone must not cause a unit-test task to be scheduled.
        writeFile("empty/src/test/resources/example.txt", "Not a test source")
    }

    protected fun writeFile(
        path: String,
        contents: String,
    ) {
        File(projectDir, path).apply {
            parentFile.mkdirs()
            writeText(contents)
        }
    }
}
