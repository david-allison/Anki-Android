// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.libanki

import com.ichi2.anki.common.utils.ext.cardsOrNotes
import com.ichi2.anki.libanki.testutils.InMemoryAnkiTest
import com.ichi2.anki.model.CardsOrNotes
import org.junit.Test
import kotlin.test.assertEquals

class BrowserModeTest : InMemoryAnkiTest() {
    @Test
    fun `browser mode helpers use the mapped config property`() {
        CardsOrNotes.NOTES.saveToCollection(col)
        assertEquals(CardsOrNotes.NOTES, col.config.cardsOrNotes)
        assertEquals(CardsOrNotes.NOTES, CardsOrNotes.fromCollection(col))

        col.config.cardsOrNotes = CardsOrNotes.CARDS
        assertEquals(CardsOrNotes.CARDS, CardsOrNotes.fromCollection(col))
    }
}
