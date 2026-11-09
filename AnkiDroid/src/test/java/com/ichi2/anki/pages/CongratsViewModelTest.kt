// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.pages

import androidx.lifecycle.ViewModelStore
import app.cash.turbine.test
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.common.destinations.StudyOptionsDestination
import com.ichi2.anki.libanki.CardType
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.libanki.QueueType
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.anki.observability.undoableOp
import com.ichi2.testutils.JvmTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// https://github.com/ankidroid/Anki-Android/issues/21393
class CongratsViewModelTest : JvmTest() {
    private val viewModel by lazy { CongratsViewModel() }

    @Test
    fun `isFilteredDeck tracks the selected deck type after refresh`() =
        runTest {
            viewModel.isFilteredDeck.test {
                assertFalse(awaitItem())
                viewModel.refreshIfNeeded()
                expectNoEvents()

                addDynamicDeck("Filtered", "")
                viewModel.refreshIfNeeded()
                assertTrue(awaitItem())

                selectDefaultDeck()
                viewModel.refreshIfNeeded()
                assertFalse(awaitItem())
            }
        }

    @Test
    fun `rebuild refills the filtered deck and can be undone`() =
        runTest {
            val note = addBasicNote()
            val deckId = addDynamicDeck("Filtered", "")
            withCol { sched.emptyFilteredDeck(deckId) }
            viewModel.refreshIfNeeded()

            viewModel.studyOptionsDestination.test {
                viewModel.onRebuild()

                assertEquals(StudyOptionsDestination, awaitItem())
                assertEquals(deckId, note.firstCard().did)
                withCol { undo() }
                assertEquals(Consts.DEFAULT_DECK_ID, note.firstCard().did)
                expectNoEvents()
            }
        }

    @Test
    fun `rebuild with no matching cards requests a reload`() =
        runTest {
            addDynamicDeck("Filtered", "deck:NonExistentDeck")
            viewModel.refreshIfNeeded()

            viewModel.congratsRefreshState.test {
                viewModel.onRebuild()

                assertEquals(CongratsRefreshState.ReloadPage, awaitItem())
                expectNoEvents()
            }
        }

    @Test
    fun `switching filtered decks preserves visibility and rebuilds the selected deck`() =
        runTest {
            val note = addBasicNote()
            addDynamicDeck("First", "deck:NonExistentDeck")

            viewModel.isFilteredDeck.test {
                assertFalse(awaitItem())
                viewModel.refreshIfNeeded()
                assertTrue(awaitItem())

                val selectedDeckId = addDynamicDeck("Second", "")
                withCol { sched.emptyFilteredDeck(selectedDeckId) }
                viewModel.refreshIfNeeded()
                expectNoEvents()

                viewModel.studyOptionsDestination.test {
                    viewModel.onRebuild()

                    assertEquals(StudyOptionsDestination, awaitItem())
                    assertEquals(selectedDeckId, note.firstCard().did)
                }
                expectNoEvents()
            }
        }

    @Test
    fun `empty returns retained cards to their original deck and can be undone`() =
        runTest {
            val note = addBasicNote()
            val deckId = addDynamicDeck("Filtered", "")
            note.firstCard().update {
                type = CardType.Rev
                queue = QueueType.Rev
                due = col.sched.today + 1
            }
            viewModel.refreshIfNeeded()

            viewModel.congratsRefreshState.test {
                viewModel.onEmpty()

                assertEquals(CongratsRefreshState.ReloadPage, awaitItem())
                assertEquals(Consts.DEFAULT_DECK_ID, note.firstCard().did)
                withCol { undo() }
                assertEquals(deckId, note.firstCard().did)
                expectNoEvents()
            }
        }

    @Test
    fun `collection changes request study options after a rebuild refills the deck`() =
        runTest {
            addBasicNote("Front", "Back")
            val deckId = addDynamicDeck("Filtered", "")
            withCol { sched.emptyFilteredDeck(deckId) }

            viewModel.studyOptionsDestination.test {
                undoableOp { sched.rebuildFilteredDeck(deckId) }
                assertEquals(StudyOptionsDestination, awaitItem())
            }
        }

    @Test
    fun `onStudyQueuesChanged - reloads the page when rebuild finds no cards`() =
        runTest {
            addBasicNote("Front", "Back")
            val deckId = addDynamicDeck("Filtered", "deck:NonExistentDeck")

            withCol { sched.rebuildFilteredDeck(deckId) }

            viewModel.congratsRefreshState.test {
                viewModel.onStudyQueuesChanged()
                assertEquals(CongratsRefreshState.ReloadPage, awaitItem())
            }
        }

    @Test
    fun `collection changes from this ViewModel are handled by their caller`() =
        runTest {
            addBasicNote()
            val deckId = addDynamicDeck("Filtered", "")
            withCol { sched.emptyFilteredDeck(deckId) }

            viewModel.studyOptionsDestination.test {
                undoableOp(handler = viewModel) { sched.rebuildFilteredDeck(deckId) }
                expectNoEvents()
            }
        }

    @Test
    fun `custom study completion requests study options`() =
        runTest {
            viewModel.studyOptionsDestination.test {
                viewModel.onCustomStudyCompleted().join()
                assertEquals(StudyOptionsDestination, awaitItem())
            }
        }

    @Test
    fun `navigation waits for a collector and is cleared after opening`() =
        runTest {
            viewModel.onCustomStudyCompleted().join()

            viewModel.studyOptionsDestination.test {
                assertEquals(StudyOptionsDestination, awaitItem())
                viewModel.onStudyOptionsOpened()
            }
            viewModel.studyOptionsDestination.test { expectNoEvents() }
        }

    @Test
    fun `unbury requests study options once`() =
        runTest {
            val card = addBasicNote().firstCard()
            withCol { sched.buryCards(listOf(card.id), manual = true) }

            viewModel.studyOptionsDestination.test {
                viewModel.onUnbury()
                assertEquals(StudyOptionsDestination, awaitItem())
                expectNoEvents()
            }
        }

    @Test
    fun `clearing the ViewModel unsubscribes from collection changes`() {
        val subscribers = ChangeManager.subscriberCount
        val store = ViewModelStore()
        try {
            store.put("congrats", viewModel)
            assertEquals(subscribers + 1, ChangeManager.subscriberCount)
        } finally {
            store.clear()
        }
        assertEquals(subscribers, ChangeManager.subscriberCount)
    }
}
