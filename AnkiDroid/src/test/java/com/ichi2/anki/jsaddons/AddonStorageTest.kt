// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.jsaddons

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidJsAPIConstants.CURRENT_JS_API_VERSION
import com.ichi2.anki.RobolectricTest
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.io.FileMatchers.anExistingDirectory
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertIs

@RunWith(AndroidJUnit4::class)
class AddonStorageTest : RobolectricTest() {
    private lateinit var storage: AddonStorage

    @Before
    override fun setUp() {
        super.setUp()
        storage = AddonStorage(targetContext)
    }

    /** Lays an addon out on disk the way an extracted npm tarball would be */
    private fun installAddon(
        name: String,
        manifest: String,
    ) {
        val manifestFile = storage.getManifestFile(name)
        manifestFile.parentFile!!.mkdirs()
        manifestFile.writeText(manifest)
    }

    @Test
    fun emptyAddonsDirReturnsNothingTest() {
        assertEquals(emptyList<InstalledAddon>(), storage.getInstalledAddons())
    }

    @Test
    fun validAddonIsListedTest() {
        installAddon("test-addon", validManifest)

        val addons = storage.getInstalledAddons()

        assertEquals(1, addons.size)
        assertEquals("test-addon", addons[0].directoryName)
        val model = assertIs<AddonValidationResult.Valid>(addons[0].result).addonModel
        assertEquals("valid-ankidroid-js-addon-test", model.name)
    }

    @Test
    fun corruptAddonDoesNotAbortListingTest() {
        installAddon("corrupt-addon", "{ not json")
        installAddon("test-addon", validManifest)

        val addons = storage.getInstalledAddons().sortedBy { it.directoryName }

        assertEquals(2, addons.size)
        assertIs<AddonValidationResult.Invalid>(addons[0].result, "a corrupt manifest is reported, not thrown")
        assertIs<AddonValidationResult.Valid>(addons[1].result, "the valid addon is still listed")
    }

    @Test
    fun plainFilesInAddonsDirAreIgnoredTest() {
        // a future AnkiDroid may keep bookkeeping files next to the addon directories
        File(storage.getAddonsDir(), "some-future-bookkeeping.json").writeText("{}")

        assertEquals(emptyList<InstalledAddon>(), storage.getInstalledAddons())
    }

    @Test
    fun deleteAddonRemovesItsDirectoryTest() {
        installAddon("test-addon", validManifest)

        assertTrue(storage.deleteAddon("test-addon"))

        assertEquals(emptyList<InstalledAddon>(), storage.getInstalledAddons())
        assertThat(storage.getAddonDir("test-addon"), not(anExistingDirectory()))
    }

    private val validManifest =
        """
        {
          "name": "valid-ankidroid-js-addon-test",
          "addonTitle": "Valid AnkiDroid JS Addon",
          "version": "1.0.0",
          "main": "index.js",
          "ankidroidJsApi": "$CURRENT_JS_API_VERSION",
          "addonType": "reviewer",
          "homepage": "https://example.com",
          "keywords": ["ankidroid-js-addon"]
        }
        """.trimIndent()
}
