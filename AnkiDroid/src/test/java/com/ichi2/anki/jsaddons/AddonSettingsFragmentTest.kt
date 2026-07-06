// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.jsaddons

import android.widget.LinearLayout
import androidx.fragment.app.testing.FragmentScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.materialswitch.MaterialSwitch
import com.ichi2.anki.AnkiDroidJsAPIConstants.CURRENT_JS_API_VERSION
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddonSettingsFragmentTest : RobolectricTest() {
    private fun installConfigurableAddon(name: String = "test-addon") {
        val manifest = storage().getManifestFile(name)
        manifest.parentFile!!.mkdirs()
        manifest.writeText(
            """
            {
              "name": "$name",
              "addonTitle": "Test addon",
              "version": "1.0.0",
              "main": "index.js",
              "ankidroidJsApi": "$CURRENT_JS_API_VERSION",
              "addonType": "reviewer",
              "homepage": "https://example.com",
              "keywords": ["ankidroid-js-addon"],
              "settings": [
                { "type": "heading", "title": "Options" },
                { "type": "toggle", "key": "vibrate", "title": "Vibrate", "default": false }
              ]
            }
            """.trimIndent(),
        )
    }

    private fun storage() = AddonStorage(targetContext)

    private fun collectSwitches(parent: LinearLayout): List<MaterialSwitch> =
        (0 until parent.childCount).flatMap { i ->
            when (val child = parent.getChildAt(i)) {
                is MaterialSwitch -> listOf(child)
                is LinearLayout -> collectSwitches(child)
                else -> emptyList()
            }
        }

    @Test
    fun togglingAWidgetPersistsTheValueTest() {
        installConfigurableAddon()

        FragmentScenario
            .launch(
                fragmentClass = AddonSettingsFragment::class.java,
                fragmentArgs = AddonSettingsFragment.arguments("test-addon"),
                themeResId = R.style.Theme_Light,
            ).use { scenario ->
                scenario.onFragment { fragment ->
                    val container = fragment.requireView().findViewById<LinearLayout>(R.id.settings_container)
                    val toggle = collectSwitches(container).single()
                    assertTrue("the widget starts at the schema default (false)", !toggle.isChecked)
                    toggle.isChecked = true
                }
            }

        // the value round-trips through the store, independent of the fragment instance
        val stored = AddonStateStore(targetContext).getSettingsValues("test-addon")
        assertEquals(true, (stored["vibrate"] as? JsonPrimitive)?.booleanOrNull)
    }

    @Test
    fun schemaLessAddonShowsThePlaceholderTest() {
        val manifest = storage().getManifestFile("plain-addon")
        manifest.parentFile!!.mkdirs()
        manifest.writeText(
            """
            {
              "name": "plain-addon", "addonTitle": "Plain", "version": "1.0.0", "main": "index.js",
              "ankidroidJsApi": "$CURRENT_JS_API_VERSION", "addonType": "reviewer",
              "homepage": "https://example.com", "keywords": ["ankidroid-js-addon"]
            }
            """.trimIndent(),
        )

        FragmentScenario
            .launch(
                fragmentClass = AddonSettingsFragment::class.java,
                fragmentArgs = AddonSettingsFragment.arguments("plain-addon"),
                themeResId = R.style.Theme_Light,
            ).use { scenario ->
                scenario.onFragment { fragment ->
                    val placeholder = fragment.requireView().findViewById<android.view.View>(R.id.no_settings_placeholder)
                    assertEquals("the 'no settings' placeholder is shown", android.view.View.VISIBLE, placeholder.visibility)
                }
            }
    }
}
