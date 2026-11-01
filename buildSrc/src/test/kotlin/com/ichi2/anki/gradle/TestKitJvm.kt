// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import org.gradle.testkit.runner.GradleRunner

/** Use first-tier compilation for short fixture builds, retaining Gradle's default memory limits. */
fun GradleRunner.withTestDaemonJvm(maxWorkers: Int = 1): GradleRunner {
    val options =
        listOf(
            "-Xms256m",
            "-Xmx512m",
            "-XX:MaxMetaspaceSize=384m",
            "-XX:+HeapDumpOnOutOfMemoryError",
            "-XX:TieredStopAtLevel=1",
            "-XX:ReservedCodeCacheSize=240m",
        ).joinToString(" ")
    return withArguments(arguments + listOf("--max-workers=$maxWorkers", "-Dorg.gradle.jvmargs=$options"))
}
