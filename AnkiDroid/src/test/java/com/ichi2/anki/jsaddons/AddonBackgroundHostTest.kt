// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.jsaddons

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidJsAPIConstants.CURRENT_JS_API_VERSION
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.settings.Prefs
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AddonBackgroundHostTest : RobolectricTest() {
    @Before
    override fun setUp() {
        super.setUp()
        Prefs.devAddonsEnabled = true
    }

    private fun installBackgroundAddon(
        name: String = "bg-addon",
        background: String? = "background.js",
    ) {
        val storage = AddonStorage(targetContext)
        val manifest = storage.getManifestFile(name)
        manifest.parentFile!!.mkdirs()
        val bg = background?.let { "\"background\": \"$it\"," } ?: ""
        manifest.writeText(
            """
            {
              "name": "$name", "addonTitle": "BG", "version": "1.0.0", "main": "index.js",
              "ankidroidJsApi": "$CURRENT_JS_API_VERSION", "addonType": "reviewer",
              "homepage": "https://example.com", "keywords": ["ankidroid-js-addon"],
              $bg
              "settings": [ { "type": "number", "key": "n", "title": "N", "default": 7 } ]
            }
            """.trimIndent(),
        )
        if (background != null) File(manifest.parentFile, background).writeText("ankidroid.log('hello');")
    }

    @Test
    fun onlyEnabledBackgroundAddonsArePreparedTest() {
        installBackgroundAddon("bg-addon")
        installBackgroundAddon("no-bg-addon", background = null)
        AddonStateStore(targetContext).setEnabled("bg-addon", true)
        AddonStateStore(targetContext).setEnabled("no-bg-addon", true)

        val prepared = AddonBackgroundHost.prepareBackgroundAddons(targetContext)

        assertEquals(1, prepared.size)
        assertEquals("bg-addon", prepared[0].name)
    }

    @Test
    fun disabledBackgroundAddonIsNotPreparedTest() {
        installBackgroundAddon("bg-addon")

        assertEquals(emptyList<BackgroundAddon>(), AddonBackgroundHost.prepareBackgroundAddons(targetContext))
    }

    @Test
    fun nothingIsPreparedWhenDevFlagOffTest() {
        installBackgroundAddon("bg-addon")
        AddonStateStore(targetContext).setEnabled("bg-addon", true)
        Prefs.devAddonsEnabled = false

        assertEquals(emptyList<BackgroundAddon>(), AddonBackgroundHost.prepareBackgroundAddons(targetContext))
    }

    @Test
    fun hostPageSandboxesEachAddonAndBakesSettingsTest() {
        val addon = BackgroundAddon("bg-addon", "ankidroid.log('hi');", JsonObject(mapOf("n" to JsonPrimitive(7))))

        val html = AddonBackgroundHost.hostPageHtml(listOf(addon))

        assertTrue("each addon is sandboxed", html.contains("sandbox=\"allow-scripts\""))
        assertFalse("the sandbox does not grant same-origin", html.contains("allow-same-origin"))
        // the addon script and its resolved settings are baked into the (escaped) srcdoc
        assertTrue("resolved settings are baked in", html.contains("\\\"n\\\":7"))
        // a script closing tag in addon code cannot break out of the host inline script
        assertFalse("no unescaped break-out", html.contains("</script>ankidroid"))
    }

    @Test
    fun backgroundBridgePersistsSettingWritesTest() {
        installBackgroundAddon("bg-addon")
        AddonStateStore(targetContext).setEnabled("bg-addon", true)

        AddonBackgroundHost.BackgroundBridge(targetContext).setSetting("bg-addon", "n", "42")

        assertEquals(JsonPrimitive(42), AddonStateStore(targetContext).getSettingsValues("bg-addon")["n"])
    }
}
