// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.testutils.JvmTest
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BrowserRowCollectionTest : JvmTest() {
    @Test
    fun `notes preview resolves the first cards in browser order without per-note lookups`() =
        runTest {
            val basic = addBasicAndReversedNote()
            val cloze = addClozeNote("{{c4::four}}")
            // Add a lower ordinal later: the first card need not have the lowest ID or ordinal zero.
            cloze.setField(0, "{{c2::two}} {{c4::four}}")
            assertTrue(col.updateNote(cloze).note)
            addBasicNote("outside the search")
            val expected = listOf(cloze.cardIds(col).first(), basic.cardIds(col).first())
            val rows = BrowserRowCollection(CardsOrNotes.NOTES, mutableListOf(CardOrNoteId(cloze.id), CardOrNoteId(basic.id)))
            val backend = col.backend
            val database = col.db
            mockkObject(backend, database)
            try {
                assertEquals(expected, rows.queryOneCardIdPerRow())
                // Issue 23272: thousands of notes must not cause thousands of backend round trips.
                verify(exactly = 0) { backend.cardsOfNote(nid = any()) }
                verify(exactly = 1) { database.query(any(), *anyVararg()) }
            } finally {
                unmockkObject(backend, database)
            }
        }

    @Test
    fun `empty notes preview is empty`() =
        runTest {
            val rows = BrowserRowCollection(CardsOrNotes.NOTES, mutableListOf())
            assertEquals(emptyList(), rows.queryOneCardIdPerRow())
        }

    @Test
    fun `cards preview preserves every card in browser order`() =
        runTest {
            val ids = addBasicAndReversedNote().cardIds(col).reversed()
            val rows = BrowserRowCollection(CardsOrNotes.CARDS, ids.map(::CardOrNoteId).toMutableList())
            assertEquals(ids, rows.queryOneCardIdPerRow())
        }
}
