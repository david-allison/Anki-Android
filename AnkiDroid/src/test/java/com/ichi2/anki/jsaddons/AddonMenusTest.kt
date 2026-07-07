// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.jsaddons

import android.view.Menu
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidJsAPIConstants.CURRENT_JS_API_VERSION
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.settings.Prefs
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.fakes.RoboMenu
import org.robolectric.fakes.RoboMenuItem

@RunWith(AndroidJUnit4::class)
class AddonMenusTest : RobolectricTest() {
    @Before
    override fun setUp() {
        super.setUp()
        Prefs.devAddonsEnabled = true
    }

    private fun installMenuAddon(
        name: String,
        screen: String = AddonMenus.DECK_PICKER,
        enabled: Boolean = true,
    ) {
        val manifest = AddonStorage(targetContext).getManifestFile(name)
        manifest.parentFile!!.mkdirs()
        manifest.writeText(
            """
            {
              "name": "$name", "addonTitle": "$name", "version": "1.0.0", "main": "index.js",
              "ankidroidJsApi": "$CURRENT_JS_API_VERSION", "addonType": "reviewer",
              "homepage": "https://example.com", "keywords": ["ankidroid-js-addon"],
              "menus": [ { "screen": "$screen", "id": "do-thing", "title": "$name action" } ]
            }
            """.trimIndent(),
        )
        if (enabled) AddonStateStore(targetContext).setEnabled(name, true)
    }

    private fun emptyMenu(): Menu = RoboMenu(targetContext)

    @Test
    fun contributionsAreSelectedPerScreenTest() {
        installMenuAddon("dp-addon", screen = AddonMenus.DECK_PICKER)
        installMenuAddon("other-addon", screen = "some-other-screen")

        val forDeckPicker = AddonMenus.contributionsForScreen(targetContext, AddonMenus.DECK_PICKER)
        assertEquals(listOf("dp-addon"), forDeckPicker.map { it.addonName })
    }

    @Test
    fun disabledAddonContributesNothingTest() {
        installMenuAddon("dp-addon", enabled = false)
        assertEquals(emptyList<AddonMenuContribution>(), AddonMenus.contributionsForScreen(targetContext, AddonMenus.DECK_PICKER))
    }

    @Test
    fun nothingContributedWhenDevFlagOffTest() {
        installMenuAddon("dp-addon")
        Prefs.devAddonsEnabled = false
        assertEquals(emptyList<AddonMenuContribution>(), AddonMenus.contributionsForScreen(targetContext, AddonMenus.DECK_PICKER))
    }

    @Test
    fun populateAddsItemsAndDispatchesOnClickTest() {
        installMenuAddon("dp-addon")
        val menu = emptyMenu()
        val dispatched = mutableListOf<AddonMenuContribution>()

        val added = AddonMenus.populate(menu, targetContext, AddonMenus.DECK_PICKER, dispatch = { dispatched.add(it) })

        assertEquals(1, added)
        assertEquals("dp-addon action", menu.getItem(0).title)
        // clicking the item dispatches its contribution
        (menu.getItem(0) as RoboMenuItem).click()
        assertTrue("the click dispatched the contribution", dispatched.any { it.addonName == "dp-addon" && it.id == "do-thing" })
    }
}
