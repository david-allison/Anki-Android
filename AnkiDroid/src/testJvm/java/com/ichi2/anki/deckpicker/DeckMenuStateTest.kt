// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>

package com.ichi2.anki.deckpicker

import androidx.core.content.edit
import androidx.lifecycle.ViewModelStore
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.JvmPreferencesRule
import com.ichi2.testutils.JvmTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DeckMenuStateTest : JvmTest() {
    @get:Rule
    val preferences = JvmPreferencesRule()

    private val store = ViewModelStore()
    private val viewModel by lazy {
        preferences.preferences.edit { putBoolean(Prefs.key(R.string.sync_status_badge_key), false) }
        DeckPickerViewModel().also { store.put("deck-picker", it) }
    }

    @After
    override fun tearDown() {
        try {
            store.clear()
        } finally {
            super.tearDown()
        }
    }

    @Test
    fun `search icon follows deck count through creation and deletion`() =
        runTest {
            var expectedCount = 1
            for (i in 0 until 10) {
                val id = withCol { decks.id("Deck$i") }
                assertEquals(++expectedCount, withCol { decks.count() })

                viewModel.refreshMenuState()
                assertEquals(expectedCount >= 10, viewModel.optionsMenuState?.searchIcon)

                if (expectedCount >= 10) {
                    viewModel.deleteDeck(id).join()
                    assertEquals(--expectedCount, withCol { decks.count() })
                    viewModel.refreshMenuState()
                    assertFalse(viewModel.optionsMenuState?.searchIcon ?: true)
                }
            }
        }

    @Test
    fun `subdecks count toward search icon visibility`() =
        runTest {
            viewModel.refreshMenuState()
            assertFalse(viewModel.optionsMenuState!!.searchIcon)

            withCol { decks.id((0..10).joinToString("::") { "Deck$it" }) }
            viewModel.refreshMenuState()

            assertEquals(true, viewModel.optionsMenuState!!.searchIcon)
        }
}
