// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.jsaddons

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidJsAPIConstants.CURRENT_JS_API_VERSION
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.settings.Prefs
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AddonPageHostTest : RobolectricTest() {
    @Before
    override fun setUp() {
        super.setUp()
        Prefs.devAddonsEnabled = true
    }

    private fun installAddon(
        name: String,
        pages: String? = null,
        addonType: String = "reviewer",
        enabled: Boolean = true,
    ) {
        val storage = AddonStorage(targetContext)
        val manifest = storage.getManifestFile(name)
        manifest.parentFile!!.mkdirs()
        val pagesField = pages?.let { "\"pages\": $it," } ?: ""
        manifest.writeText(
            """
            {
              "name": "$name", "addonTitle": "$name", "version": "1.0.0", "main": "index.js",
              "ankidroidJsApi": "$CURRENT_JS_API_VERSION", "addonType": "$addonType",
              "homepage": "https://example.com", "keywords": ["ankidroid-js-addon"],
              $pagesField
              "settings": [ { "type": "number", "key": "n", "title": "N", "default": 3 } ]
            }
            """.trimIndent(),
        )
        File(manifest.parentFile, "index.js").writeText("ankidroid.log('$name loaded');")
        if (enabled) AddonStateStore(targetContext).setEnabled(name, true)
    }

    @Test
    fun addonsAreSelectedPerPageTest() {
        installAddon("reviewer-addon") // addonType reviewer -> reviewer page
        installAddon("stats-addon", pages = """["statistics"]""")

        assertEquals(listOf("reviewer-addon"), AddonPageHost.addonsForPage(targetContext, AddonPages.REVIEWER).map { it.name })
        assertEquals(listOf("stats-addon"), AddonPageHost.addonsForPage(targetContext, AddonPages.STATISTICS).map { it.name })
        assertEquals(emptyList<String>(), AddonPageHost.addonsForPage(targetContext, AddonPages.CARD_INFO).map { it.name })
    }

    @Test
    fun noBootstrapWithoutAddonsOrDevFlagTest() {
        assertNull("no bootstrap when nothing targets the page", AddonPageHost.bootstrapScript(targetContext, AddonPages.STATISTICS))

        installAddon("stats-addon", pages = """["statistics"]""")
        Prefs.devAddonsEnabled = false
        assertNull("no bootstrap when the dev flag is off", AddonPageHost.bootstrapScript(targetContext, AddonPages.STATISTICS))
    }

    @Test
    fun bootstrapSandboxesEachAddonWithBakedSettingsTest() {
        installAddon("stats-addon", pages = """["statistics"]""")

        val script = AddonPageHost.bootstrapScript(targetContext, AddonPages.STATISTICS)!!

        assertTrue("each addon is sandboxed", script.contains("frame.sandbox = \"allow-scripts\""))
        assertFalse("the sandbox does not grant same-origin", script.contains("allow-same-origin"))
        assertTrue("the page id is provided", script.contains("\\\"statistics\\\"") || script.contains("statistics"))
        assertTrue("resolved settings are baked in", script.contains("\\\"n\\\":3"))
        assertTrue("exposes the relay API", script.contains("ankidroid.addElement") || script.contains("addElement"))
    }

    @Test
    fun bootstrapNeutralisesScriptBreakoutTest() {
        // an addon whose code contains a closing script tag must not break out of the srcdoc
        val storage = AddonStorage(targetContext)
        installAddon("evil-addon", pages = """["statistics"]""")
        File(storage.getAddonDir("evil-addon"), "package/index.js").writeText("</script><script>alert(1)</script>")

        val script = AddonPageHost.bootstrapScript(targetContext, AddonPages.STATISTICS)!!

        assertFalse("the breakout attempt is neutralised", script.contains("</script><script>alert(1)"))
    }

    @Test
    fun pageBridgePersistsSettingWritesTest() {
        installAddon("stats-addon", pages = """["statistics"]""")

        AddonPageHost.AddonPageBridge(targetContext).setSetting("stats-addon", "n", "9")

        assertEquals(JsonPrimitive(9), AddonStateStore(targetContext).getSettingsValues("stats-addon")["n"])
    }

    @Test
    fun disabledAddonIsNotInjectedTest() {
        installAddon("stats-addon", pages = """["statistics"]""", enabled = false)

        assertEquals(emptyList<PageAddon>(), AddonPageHost.preparePageAddons(targetContext, AddonPages.STATISTICS))
    }
}
