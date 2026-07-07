// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.jsaddons

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.utils.FileOperation
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertIs

@RunWith(AndroidJUnit4::class)
class AddonRegistryTest : RobolectricTest() {
    @Test
    fun fetchesAndParsesTheIndexTest() {
        // the existing index fixture is an array of addon manifests, like a registry index
        val indexUrl = File(FileOperation.getFileResource("test-js-addon.json")).toURI().toURL()

        val result = AddonRegistry(indexUrl).fetchAvailableAddons()

        val addons = assertIs<AddonRegistry.FetchResult.Success>(result).addons
        assertTrue("the index yields installable addons", addons.isNotEmpty())
        assertTrue("each has a tarball to download", addons.all { it.dist?.tarball?.endsWith(".tgz") == true })
    }

    @Test
    fun unreachableIndexFailsGracefullyTest() {
        val result = AddonRegistry(File("/does/not/exist/index.json").toURI().toURL()).fetchAvailableAddons()

        assertIs<AddonRegistry.FetchResult.Failure>(result)
    }

    @Test
    fun computesAvailableUpdatesTest() {
        val available =
            listOf(
                model("addon-a", "2.0.0"), // installed 1.0.0 -> update
                model("addon-b", "1.0.0"), // installed 1.0.0 -> no update
                model("addon-c", "1.5.0"), // not installed -> ignored
                model("addon-d", "not-a-version"), // unparseable -> skipped, not an update
            )
        val installed = mapOf("addon-a" to "1.0.0", "addon-b" to "1.0.0", "addon-d" to "1.0.0")

        val updates = computeAvailableUpdates(available, installed)

        assertEquals(listOf(AddonUpdate("addon-a", "1.0.0", "2.0.0")), updates)
    }

    private fun model(
        name: String,
        version: String,
    ) = AddonModel(
        name = name,
        addonTitle = name,
        icon = "",
        version = version,
        description = "",
        main = "index.js",
        ankidroidJsApi = "0.0.3",
        addonType = "reviewer",
        keywords = listOf("ankidroid-js-addon"),
        author = emptyMap(),
        license = "",
        homepage = "https://example.com",
        dist = DistInfo("https://example.com/$name.tgz"),
    )
}
