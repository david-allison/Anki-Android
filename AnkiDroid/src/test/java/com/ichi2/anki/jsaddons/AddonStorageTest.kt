// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.jsaddons

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidJsAPIConstants.CURRENT_JS_API_VERSION
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.RobolectricTest
import com.ichi2.testutils.ShadowStatFs
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.io.FileMatchers.anExistingDirectory
import org.hamcrest.io.FileMatchers.anExistingFile
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.GZIPOutputStream
import kotlin.test.assertIs

@RunWith(AndroidJUnit4::class)
class AddonStorageTest : RobolectricTest() {
    private lateinit var storage: AddonStorage

    @Before
    override fun setUp() {
        super.setUp()
        // the extractor used by installFromTarball checks free disk space
        ShadowStatFs.markAsNonEmpty(CollectionHelper.getCurrentAnkiDroidDirectory(targetContext))
        storage = AddonStorage(targetContext)
    }

    @After
    override fun tearDown() {
        super.tearDown()
        ShadowStatFs.reset()
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

    @Test
    fun installFromTarballInstallsValidAddonTest() {
        val tarball = buildTarball(mapOf("package.json" to validManifest, "index.js" to "// js"))

        val result = storage.installFromTarball(tarball)

        val model = assertIs<AddonValidationResult.Valid>(result, "a valid tarball installs").addonModel
        assertEquals("valid-ankidroid-js-addon-test", model.name)
        assertThat(storage.getManifestFile(model.name), anExistingFile())
        assertEquals(1, storage.getInstalledAddons().size)
        assertNoStagingLeftovers()
    }

    @Test
    fun installFromTarballReplacesExistingAddonTest() {
        val tarball = buildTarball(mapOf("package.json" to validManifest, "index.js" to "// js"))
        assertIs<AddonValidationResult.Valid>(storage.installFromTarball(tarball))
        // a file from a previous version must not survive the reinstall
        val leftover = File(storage.getAddonDir("valid-ankidroid-js-addon-test"), "package/old-version-file.js")
        leftover.writeText("")

        assertIs<AddonValidationResult.Valid>(storage.installFromTarball(tarball))

        assertFalse("a file from the previous install must not survive", leftover.exists())
        assertEquals(1, storage.getInstalledAddons().size)
        assertNoStagingLeftovers()
    }

    /** Builds an npm-style tarball: file contents keyed by path, nested under `package/` */
    private fun buildTarball(files: Map<String, String>): File {
        val tarball = File.createTempFile("addon", ".tgz")
        TarArchiveOutputStream(GZIPOutputStream(tarball.outputStream())).use { tar ->
            for ((path, content) in files) {
                val bytes = content.encodeToByteArray()
                val entry = TarArchiveEntry("package/$path").apply { size = bytes.size.toLong() }
                tar.putArchiveEntry(entry)
                tar.write(bytes)
                tar.closeArchiveEntry()
            }
        }
        return tarball
    }

    @Test
    fun corruptTarballInstallsNothingTest() {
        val notATarball = File.createTempFile("not-an-addon", ".tgz").apply { writeText("not a tarball") }

        val result = storage.installFromTarball(notATarball)

        assertIs<AddonValidationResult.Invalid>(result, "a corrupt tarball is rejected")
        assertEquals(emptyList<InstalledAddon>(), storage.getInstalledAddons())
        assertNoStagingLeftovers()
    }

    private fun assertNoStagingLeftovers() {
        val hidden = storage.getAddonsDir().listFiles { file -> file.isHidden }.orEmpty()
        assertEquals("no staging directories are left behind", 0, hidden.size)
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
