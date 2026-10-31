// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import androidx.core.content.edit
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.ichi2.anki.libanki.BrowserConfig
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.servicelayer.PreferenceUpgradeService.PreferenceUpgrade
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class BrowserColumnPreferencesTest {
    private val preferences = SPMockBuilder().createSharedPreferences()

    @Test
    fun `invalid preference types use defaults`() {
        preferences.edit { putInt(BrowserConfig.ACTIVE_CARD_COLUMNS_KEY, 42) }
        assertEquals(
            listOf("noteFld", "template", "cardDue", "deck"),
            BrowserColumnCollection.load(preferences, CardsOrNotes.CARDS).backendKeys.toList(),
        )
    }

    @Test
    fun `column updates persist only to the requested mode`() {
        BrowserColumnCollection.replace(preferences, CardsOrNotes.NOTES, listOf(CardBrowserColumn.QUESTION, CardBrowserColumn.DECK))
        assertEquals("question|deck", preferences.getString(BrowserConfig.ACTIVE_NOTE_COLUMNS_KEY, null))
        assertFalse(preferences.contains(BrowserConfig.ACTIVE_CARD_COLUMNS_KEY))
        assertEquals(
            listOf(CardBrowserColumn.QUESTION, CardBrowserColumn.DECK),
            BrowserColumnCollection.load(preferences, CardsOrNotes.NOTES).columns,
        )
    }

    @Test
    fun `legacy column indices are migrated through the platform preferences adapter`() {
        preferences.edit {
            putInt("cardBrowserColumn1", 1)
            putInt("cardBrowserColumn2", 5)
        }
        PreferenceUpgrade.UpgradeBrowserColumns().performUpgrade(preferences)
        assertEquals(
            listOf(CardBrowserColumn.SFLD, CardBrowserColumn.TAGS),
            BrowserColumnCollection.load(preferences, CardsOrNotes.CARDS).columns.take(2),
        )
        assertEquals(
            listOf(CardBrowserColumn.SFLD, CardBrowserColumn.TAGS),
            BrowserColumnCollection.load(preferences, CardsOrNotes.NOTES).columns.take(2),
        )
        assertFalse(preferences.contains("cardBrowserColumn1"))
        assertFalse(preferences.contains("cardBrowserColumn2"))
    }
}
