// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/** Snapshots Gradle's configured project graph, including fixtures and convention plugins. */
fun Project.registerTestModuleSelection() {
    val selectionRoot = rootProject
    val selectionTask =
        tasks.register<SelectAffectedTestModules>("selectAffectedTestModules") {
            group = "verification"
            description = "Selects affected unit-test modules relative to -PciTestBase=<commit>."
            repositoryDirectory.set(selectionRoot.layout.projectDirectory)
            comparisonBase.convention(providers.gradleProperty("ciTestBase").orElse(""))
            outputFile.convention(selectionRoot.layout.buildDirectory.file("ci/affected-test-modules.properties"))
        }

    gradle.projectsEvaluated {
        // Copy the complete graph after every project has declared its dependencies,
        // even if the task was realized early. Execution only reads these inputs.
        selectionTask.configure {
            moduleDirectories.set(
                selectionRoot.subprojects
                    // Implicit parent projects do not own tests; unknown paths fall back to all modules.
                    .filter { it.buildFile.isFile }
                    .associate {
                        it.path to it.projectDir.relativeTo(selectionRoot.projectDir).invariantSeparatorsPath
                    },
            )
            moduleDependencies.set(
                selectionRoot.subprojects.associate { module ->
                    module.path to
                        module.configurations
                            // Lint and baseline profile generators do not affect unit-test inputs.
                            .filterNot { it.name == "lintChecks" || it.name.endsWith("baselineProfile", ignoreCase = true) }
                            .flatMap { configuration ->
                                configuration.allDependencies.withType(ProjectDependency::class.java).map { it.path }
                            }.distinct()
                },
            )
        }
    }
}

@DisableCachingByDefault(because = "Git HEAD can change without Gradle inputs changing")
abstract class SelectAffectedTestModules : DefaultTask() {
    @get:Internal
    abstract val repositoryDirectory: DirectoryProperty

    @get:Input
    abstract val comparisonBase: Property<String>

    @get:Input
    abstract val moduleDirectories: MapProperty<String, String>

    @get:Input
    abstract val moduleDependencies: MapProperty<String, List<String>>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    init {
        // Recompute even when a configuration-cache entry is reused after a commit.
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun select() {
        val paths = changedPaths()
        val modules = paths?.let { affectedTestModules(it, moduleDirectories.get(), moduleDependencies.get()) }
        val selection = testSelectionOutput(modules)
        logger.lifecycle("Test selection:\n$selection")
        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(selection)
        }
    }

    private fun changedPaths(): List<String>? {
        val base = comparisonBase.get()
        // No comparison base means a full local, push, scheduled or manual run.
        if (base.isEmpty()) return null
        if (!base.matches(Regex("[0-9a-fA-F]{40}"))) {
            logger.warn("Invalid comparison base; running all tests")
            return null
        }
        val output = ByteArrayOutputStream()
        val errors = ByteArrayOutputStream()
        val result =
            execOperations.exec {
                workingDir(repositoryDirectory.get().asFile)
                // HEAD is the tested merge commit. Include both sides of renames.
                commandLine("git", "diff", "--name-only", "--no-renames", "-z", base, "HEAD", "--")
                standardOutput = output
                errorOutput = errors
                isIgnoreExitValue = true
            }
        if (result.exitValue != 0) {
            logger.warn("Could not compare Git revisions; running all tests: $errors")
            return null
        }
        return output.toString(Charsets.UTF_8).split('\u0000').filter { it.isNotEmpty() }
    }
}

/** Null means a full run; an empty set means no affected modules. */
internal fun affectedTestModules(
    paths: List<String>,
    directories: Map<String, String>,
    dependencies: Map<String, List<String>>,
): Set<String>? {
    val modules = directories.entries.sortedByDescending { it.value.length }
    val affected = mutableSetOf<String>()
    val changedProductionModules = mutableSetOf<String>()
    for (path in paths) {
        // Removing a dependency changes the graph. Include its old dependents too.
        if (path.endsWith(".gradle") || path.endsWith(".gradle.kts")) return null
        val module = modules.firstOrNull { path.startsWith("${it.value}/") }
        when {
            module != null -> {
                affected.add(module.key)
                // A module's private unit tests are not inputs to dependent modules.
                if (!path.startsWith("${module.value}/src/test/")) changedProductionModules.add(module.key)
            }
            path.startsWith("docs/") || ('/' !in path && path.endsWith(".md")) -> continue
            // Includes shared build configuration, CI scripts and unknown paths.
            else -> return null
        }
    }
    do {
        val dependents = dependencies.filterValues { paths -> paths.any { it in changedProductionModules } }.keys
        val expanded = changedProductionModules.addAll(dependents)
    } while (expanded)
    affected.addAll(changedProductionModules)
    return affected
}

internal fun testSelectionOutput(modules: Set<String>?): String {
    // Lint rules and baseline profiles have dedicated lint and emulator jobs.
    val unitModules = modules?.minus(setOf(":lint-rules", ":baselineprofile"))
    return "modules=${unitModules?.sorted()?.joinToString(",") ?: "*"}\n"
}
