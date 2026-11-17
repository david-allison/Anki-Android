// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TestModuleSelectionTest {
    private val directories =
        mapOf(
            ":AnkiDroid" to "AnkiDroid",
            ":common" to "common",
            ":common:android" to "common/android",
            ":feature:browse" to "feature/browse",
            ":widgets" to "components/widgets",
        )
    private val dependencies =
        mapOf(
            ":AnkiDroid" to listOf(":feature:browse", ":widgets"),
            ":feature:browse" to listOf(":common:android"),
            ":common:android" to listOf(":common"),
            ":widgets" to listOf(":common"),
        )

    @Test
    fun `app changes do not select its dependencies`() {
        assertEquals(setOf(":AnkiDroid"), select("AnkiDroid/src/main/Example.kt"))
    }

    @Test
    fun `dependency changes select transitive dependents`() {
        assertEquals(directories.keys, select("common/src/main/Example.kt"))
    }

    @Test
    fun `private unit test changes select only their own module`() {
        assertEquals(setOf(":widgets"), select("components/widgets/src/test/Example.kt"))
        assertEquals(setOf(":common"), select("common/src/test/resources/data.json"))
        assertEquals(
            setOf(":common", ":widgets", ":AnkiDroid"),
            select("common/src/test/Example.kt", "components/widgets/src/main/Example.kt"),
        )
        assertEquals(directories.keys, select("common/src/testFixtures/Fixture.kt"))
    }

    @Test
    fun `nested modules use their actual directories and longest path match`() {
        assertEquals(
            setOf(":common:android", ":feature:browse", ":AnkiDroid"),
            select("common/android/src/main/Example.kt"),
        )
        assertEquals(setOf(":widgets", ":AnkiDroid"), select("components/widgets/src/main/Example.kt"))
    }

    @Test
    fun `multiple changed modules are combined`() {
        assertEquals(
            setOf(":widgets", ":feature:browse", ":AnkiDroid"),
            select("components/widgets/src/main/Example.kt", "feature/browse/src/main/Example.kt"),
        )
    }

    @Test
    fun `cycles terminate`() {
        val cycle = dependencies + (":common" to listOf(":widgets"))
        assertEquals(directories.keys, affectedTestModules(listOf("components/widgets/src/main/Example.kt"), directories, cycle))
    }

    @Test
    fun `shared build configuration and unknown paths select everything`() {
        for (path in listOf(
            "build.gradle.kts",
            "settings.gradle.kts",
            "common/build.gradle.kts",
            "common/build.gradle",
            "gradle/libs.versions.toml",
            "buildSrc/src/main/Example.kt",
            ".github/workflows/tests_unit.yml",
            "unknown/src/main/Example.kt",
        )) {
            assertNull(select(path), path)
        }
    }

    @Test
    fun `documentation only and empty comparisons select nothing`() {
        assertEquals(emptySet<String>(), select("README.md", "docs/example.md"))
        assertEquals(emptySet<String>(), select())
        assertEquals(setOf(":AnkiDroid"), select("README.md", "AnkiDroid/src/main/Example.kt"))
    }

    @Test
    fun `output distinguishes full empty and specific selections`() {
        assertEquals("modules=*\n", testSelectionOutput(null))
        assertEquals("modules=\n", testSelectionOutput(emptySet()))
        assertEquals("modules=:common\n", testSelectionOutput(setOf(":common")))
        assertEquals(
            "modules=:AnkiDroid,:widgets\n",
            testSelectionOutput(setOf(":widgets", ":AnkiDroid")),
        )
        assertEquals("modules=\n", testSelectionOutput(setOf(":baselineprofile")))
        assertEquals("modules=\n", testSelectionOutput(setOf(":lint-rules")))
        assertEquals("modules=:common\n", testSelectionOutput(setOf(":common", ":baselineprofile", ":lint-rules")))
    }

    private fun select(vararg paths: String): Set<String>? = affectedTestModules(paths.toList(), directories, dependencies)
}
