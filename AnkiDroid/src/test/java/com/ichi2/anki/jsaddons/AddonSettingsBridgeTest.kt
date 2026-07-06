// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.jsaddons

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidJsAPIConstants.CURRENT_JS_API_VERSION
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.settings.Prefs
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddonSettingsBridgeTest : RobolectricTest() {
    private lateinit var stateStore: AddonStateStore

    @Before
    override fun setUp() {
        super.setUp()
        Prefs.devAddonsEnabled = true
        stateStore = AddonStateStore(targetContext)
    }

    private fun installConfigurableAddon(name: String = "delay-addon") {
        val manifest = AddonStorage(targetContext).getManifestFile(name)
        manifest.parentFile!!.mkdirs()
        manifest.writeText(
            """
            {
              "name": "$name", "addonTitle": "Delay", "version": "1.0.0", "main": "index.js",
              "ankidroidJsApi": "$CURRENT_JS_API_VERSION", "addonType": "reviewer",
              "homepage": "https://example.com", "keywords": ["ankidroid-js-addon"],
              "settings": [
                { "type": "number", "key": "delaySeconds", "title": "Delay", "default": 10 }
              ]
            }
            """.trimIndent(),
        )
        java.io.File(manifest.parentFile, "index.js").writeText("// js")
    }

    @Test
    fun noBootstrapWhenNoAddonEnabledTest() {
        installConfigurableAddon() // installed but not enabled
        assertNull(AddonSettingsBridge.bootstrapScript(targetContext))
    }

    @Test
    fun bootstrapBakesInResolvedDefaultsTest() {
        installConfigurableAddon()
        stateStore.setEnabled("delay-addon", true)

        val script = AddonSettingsBridge.bootstrapScript(targetContext)!!

        // the resolved default is baked into the page for synchronous reads
        assertTrue("baked settings include the addon", script.contains("\"delay-addon\""))
        assertTrue("baked settings include the default value", script.contains("\"delaySeconds\":10"))
        assertTrue("exposes the reader", script.contains("ankidroid.addonSettings"))
        assertTrue("exposes the writer", script.contains("ankidroid.setAddonSetting"))
    }

    @Test
    fun bootstrapReflectsAStoredOverrideTest() {
        installConfigurableAddon()
        stateStore.setEnabled("delay-addon", true)
        stateStore.setSettingValue("delay-addon", "delaySeconds", JsonPrimitive(30))

        val script = AddonSettingsBridge.bootstrapScript(targetContext)!!

        assertTrue("the stored override is baked in, not the default", script.contains("\"delaySeconds\":30"))
    }

    @Test
    fun handleSetPersistsTheValueTest() {
        installConfigurableAddon()
        stateStore.setEnabled("delay-addon", true)

        val body = """{"addon":"delay-addon","key":"delaySeconds","value":42}"""
        AddonSettingsBridge.handleSet(targetContext, body.encodeToByteArray())

        assertEquals(JsonPrimitive(42), stateStore.getSettingsValues("delay-addon")["delaySeconds"])
    }
}
