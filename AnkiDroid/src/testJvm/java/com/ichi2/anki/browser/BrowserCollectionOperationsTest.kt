// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.browser.CardBrowserViewModelTestBase.InitMode
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.model.SelectableDeck
import kotlinx.coroutines.flow.first
import org.junit.Test
import kotlin.test.assertEquals

/** Collection and selection regressions which do not require browser views. */
class BrowserCollectionOperationsTest : JvmCardBrowserViewModelTestBase() {
    @Test
    fun `reposition without shift has expected outcome`() =
        runViewModelTest(notes = 5, initMode = InitMode.NO_DELAY) {
            awaitRows(5)
            assertRepositionOutcome(3, 4, 1, listOf(1, 2, 3, 1, 5), shift = false)
            selectNone()
            assertRepositionOutcome(4, 5, 1, listOf(1, 2, 3, 1, 1), shift = false)
        }

    @Test
    fun `reposition with shift has expected outcome`() =
        runViewModelTest(notes = 5, initMode = InitMode.NO_DELAY) {
            awaitRows(5)
            assertRepositionOutcome(4, 5, 2, listOf(1, 3, 4, 5, 2), shift = true)
            selectNone()
            assertRepositionOutcome(3, 5, 1, listOf(2, 4, 5, 1, 3), shift = true)
        }

    @Test
    fun `long selection includes rows beyond the visible browser viewport`() =
        runViewModelTest(notes = 25, initMode = InitMode.NO_DELAY) {
            awaitRows(25)
            handleRowLongPress(getRowAtPosition(7).toRowSelection()).join()
            handleRowLongPress(getRowAtPosition(24).toRowSelection()).join()

            assertEquals(18, selectedRowCount())
            assertEquals(cards.subList(7, 25).toSet(), selectedRows.toSet())
        }

    @Test
    fun `searching all decks preserves the query while expanding the scope`() {
        addBasicNote("Hello", "World")
        val other = addDeck("Test Deck")
        col.setDeck(listOf(addBasicNote("Front", "Back").firstCard().id), other)
        runViewModelTest(initMode = InitMode.NO_DELAY) {
            flowOfInitCompleted.first { it }
            searchJob?.join()
            setSelectedDeck(SelectableDeck.Deck(other, "Test Deck"))
            setQuery("Hello").join()
            searchJob?.join()
            assertEquals(0, rowCount)

            setSelectedDeck(SelectableDeck.AllDecks)
            searchJob?.join()

            assertEquals("Hello", searchTerms)
            assertEquals(1, rowCount)
        }
    }

    @Test
    fun `notes mode returns one row for each note`() {
        addBasicAndReversedNote("Hello", "World")
        addBasicAndReversedNote("Hello", "Anki")
        runViewModelTest(initMode = InitMode.NO_DELAY) {
            awaitRows(4)

            setCardsOrNotes(CardsOrNotes.NOTES).join()
            searchJob?.join()

            assertEquals(2, rowCount)
        }
    }

    private suspend fun CardBrowserViewModel.awaitRows(expected: Int) {
        flowOfInitCompleted.first { it }
        searchJob?.join()
        assertEquals(expected, rowCount)
    }

    private suspend fun CardBrowserViewModel.assertRepositionOutcome(
        selectedPosition: Int,
        currentDue: Int,
        targetDue: Int,
        expectedDues: List<Int>,
        shift: Boolean,
    ) {
        val ids = cards.map { it.cardOrNoteId }
        assertEquals(currentDue, withCol { getCard(ids[selectedPosition]).due })
        selectRowAtPosition(selectedPosition)

        assertEquals(1, repositionSelectedRows(targetDue, 1, shuffle = false, shift = shift))
        searchJob?.join()

        assertEquals(expectedDues, withCol { ids.map { getCard(it).due } })
    }
}
