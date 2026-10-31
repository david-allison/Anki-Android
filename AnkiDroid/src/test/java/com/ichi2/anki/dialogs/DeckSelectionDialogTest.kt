// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Akshit Sinha <akshitsinha3@gmail.com>

package com.ichi2.anki.dialogs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.testutils.EmptyApplication
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = EmptyApplication::class)
class DeckSelectionDialogTest {
    @Test
    fun verifyDeckDisplayName() {
        val input = "deck::sub-deck::sub-deck2::sub-deck3"
        val expected = "sub-deck3"

        val deck = SelectableDeck.Deck(1234, input)
        val actual: String = deck.getDisplayName(ApplicationProvider.getApplicationContext<Context>())

        assertThat(actual, Matchers.equalTo(expected))
    }

    @Test
    fun testDialogCreation() {
        val decks: List<SelectableDeck> = listOf(SelectableDeck.Deck(5L, "deck"))
        val dialogTitle = "Select Deck"
        val summaryMessage = "Choose a deck from the list"

        val dialog = DeckSelectionDialog.newInstance(title = dialogTitle, templateEditorMessage = summaryMessage, decks = decks)
        assertNotNull(dialog)
        assertEquals(dialogTitle, dialog.arguments?.getString("arg_title"))
        assertEquals(summaryMessage, dialog.arguments?.getString("arg_template_editor_message"))
    }
}
