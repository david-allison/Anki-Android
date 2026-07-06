// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.jsaddons

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidJsAPIConstants.CURRENT_JS_API_VERSION
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.settings.Prefs
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AddonReviewerScriptsTest : RobolectricTest() {
    private lateinit var storage: AddonStorage
    private lateinit var stateStore: AddonStateStore

    @Before
    override fun setUp() {
        super.setUp()
        storage = AddonStorage(targetContext)
        stateStore = AddonStateStore(targetContext)
        Prefs.devAddonsEnabled = true
    }

    private fun installAddon(
        name: String,
        addonType: String = "reviewer",
        main: String = "index.js",
    ) {
        val manifestFile = storage.getManifestFile(name)
        val packageDir = manifestFile.parentFile!!
        packageDir.mkdirs()
        manifestFile.writeText(
            """
            {
              "name": "$name",
              "addonTitle": "$name",
              "version": "1.0.0",
              "main": "$main",
              "ankidroidJsApi": "$CURRENT_JS_API_VERSION",
              "addonType": "$addonType",
              "homepage": "https://example.com",
              "keywords": ["ankidroid-js-addon"]
            }
            """.trimIndent(),
        )
        if (!main.startsWith("/") && ".." !in main) {
            File(packageDir, main).writeText("// $name")
        }
    }

    @Test
    fun nothingIsInjectedWhenTheDevFlagIsOffTest() {
        installAddon("some-addon")
        stateStore.setEnabled("some-addon", true)
        Prefs.devAddonsEnabled = false

        assertEquals(emptyList<String>(), AddonReviewerScripts.addonScriptUrls(targetContext))
    }

    @Test
    fun enabledReviewerAddonIsInjectedTest() {
        installAddon("some-addon")
        stateStore.setEnabled("some-addon", true)

        assertEquals(listOf("/_addons/some-addon/index.js"), AddonReviewerScripts.addonScriptUrls(targetContext))
    }

    @Test
    fun disabledAddonIsNotInjectedTest() {
        installAddon("some-addon")

        assertEquals(emptyList<String>(), AddonReviewerScripts.addonScriptUrls(targetContext))
    }

    @Test
    fun nonReviewerAddonIsNotInjectedTest() {
        installAddon("editor-addon", addonType = "note-editor")
        installAddon("future-addon", addonType = "background")
        stateStore.setEnabled("editor-addon", true)
        stateStore.setEnabled("future-addon", true)

        assertEquals(emptyList<String>(), AddonReviewerScripts.addonScriptUrls(targetContext))
    }

    @Test
    fun unsafeMainEntryIsSkippedTest() {
        installAddon("evil-addon", main = "../../../escape.js")
        stateStore.setEnabled("evil-addon", true)

        assertEquals(emptyList<String>(), AddonReviewerScripts.addonScriptUrls(targetContext))
    }

    @Test
    fun evaluationScriptsCarryTheirSourceUrlTest() {
        installAddon("some-addon")
        stateStore.setEnabled("some-addon", true)

        val scripts = AddonReviewerScripts.addonScriptsForEvaluation(targetContext)

        assertEquals(1, scripts.size)
        assertTrue("script contains the addon's code", scripts[0].contains("// some-addon"))
        assertTrue(
            "script errors attribute to the addon",
            scripts[0].endsWith("//# sourceURL=/_addons/some-addon/index.js"),
        )
    }
}
