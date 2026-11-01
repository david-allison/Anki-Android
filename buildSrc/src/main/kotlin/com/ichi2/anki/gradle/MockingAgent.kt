// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.testing.Test
import org.gradle.process.CommandLineArgumentProvider

/** Install the existing mocking agent at JVM startup instead of attaching a helper process later. */
fun Project.configureMockingAgent() {
    configurations.matching { it.name == "testRuntimeClasspath" || it.name.endsWith("UnitTestRuntimeClasspath") }.all {
        val runtimeName = name.removeSuffix("RuntimeClasspath")
        val testName = if (runtimeName == "test") "test" else "test${runtimeName.replaceFirstChar(Char::uppercaseChar)}"
        val agent = incoming.artifactView {
            componentFilter {
                it is ModuleComponentIdentifier && it.group == "net.bytebuddy" && it.module == "byte-buddy-agent"
            }
        }.files
        tasks.withType(Test::class.java).matching { it.name == testName || it.name == testName.replace("UnitTest", "JvmUnitTest") }.configureEach {
            jvmArgumentProviders.add(MockingAgentArguments(agent))
        }
    }
}

class MockingAgentArguments(
    @get:Classpath val agent: FileCollection,
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> =
        if (agent.isEmpty) emptyList() else listOf("-javaagent:${agent.singleFile.absolutePath}")
}
