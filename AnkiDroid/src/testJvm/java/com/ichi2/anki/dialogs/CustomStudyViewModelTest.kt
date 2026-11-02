// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import androidx.lifecycle.SavedStateHandle
import anki.scheduler.CustomStudyDefaultsResponse
import anki.scheduler.CustomStudyRequest.Cram.CramKind
import anki.scheduler.customStudyDefaultsResponse
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.dialogs.customstudy.CustomStudyCardState
import com.ichi2.anki.dialogs.customstudy.CustomStudyViewModel
import com.ichi2.anki.libanki.CardType
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.QueueType
import com.ichi2.anki.libanki.sched.Scheduler
import com.ichi2.testutils.JvmTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import kotlin.test.assertFailsWith

class CustomStudyViewModelTest : JvmTest() {
    @Test
    fun `cancelling a defaults read prevents late deck selection`() =
        runTest {
            val response = customStudyDefaultsResponse { availableNew = 1 }
            val collection = mockCollectionWithSchedulerReturning(response)
            val decks = collection.decks
            val initialization = Job()
            every { collection.sched.customStudyDefaults(Consts.DEFAULT_DECK_ID) } answers {
                initialization.cancel()
                response
            }
            val original = col
            CollectionManager.setColForTests(collection)
            try {
                val viewModel = CustomStudyViewModel(SavedStateHandle(mapOf(CustomStudyViewModel.ARG_DECK_ID to Consts.DEFAULT_DECK_ID)))

                assertFailsWith<CancellationException> {
                    withContext(initialization) { viewModel.initialize() }
                }

                verify(exactly = 0) { decks.select(Consts.DEFAULT_DECK_ID) }
            } finally {
                CollectionManager.setColForTests(original)
            }
        }

    @Test
    fun `selectedKind maps selected card state index to cram kind`() {
        val viewModel = CustomStudyViewModel(SavedStateHandle())

        CustomStudyCardState.entries.forEachIndexed { index, cardState ->
            viewModel.selectedCardStateIndex = index
            assertThat(viewModel.selectedKind, equalTo(cardState.kind))
        }
    }

    @Test
    fun `selectedKind defaults to new cards when no card state is selected`() {
        val viewModel = CustomStudyViewModel(SavedStateHandle())

        viewModel.selectedCardStateIndex = -1

        assertThat(viewModel.selectedKind, equalTo(CramKind.CRAM_KIND_NEW))
    }

    @Test
    fun `'review ahead' search does not treat the deck name as a pattern`() =
        runTest {
            // '_' is a single-character wildcard in a search: "A_B" must not match "AXB"
            val deckId = addDeck("A_B")
            addBasicNote().update { moveToDeck("A_B", false) }.firstCard().update {
                queue = QueueType.Rev
                type = CardType.Rev
                due = col.sched.today + 30
            }
            addDeck("AXB")
            addNoteDueTomorrow(deckName = "AXB")

            assertThat("A_B has no cards due tomorrow", viewModel(deckId).hasCardsDueWithin(1), equalTo(false))
        }

    @Test
    fun `'review ahead' search escapes the deck name`() =
        runTest {
            // '\' starts an escape sequence in a search
            val deckId = addDeck("""A\B""")
            addNoteDueTomorrow(deckName = """A\B""")

            assertThat(viewModel(deckId).hasCardsDueWithin(1), equalTo(true))
        }

    /** Adds a note to [deckName] whose card is due tomorrow */
    private fun addNoteDueTomorrow(deckName: String) {
        addBasicNote().update { moveToDeck(deckName, false) }.firstCard().update {
            queue = QueueType.Rev
            type = CardType.Rev
            due = col.sched.today + 1
        }
    }

    private fun viewModel(deckId: DeckId) = CustomStudyViewModel(SavedStateHandle(mapOf(CustomStudyViewModel.ARG_DECK_ID to deckId)))

    private fun mockCollectionWithSchedulerReturning(response: CustomStudyDefaultsResponse): Collection {
        val tree = col.sched.deckTree(includeCounts = false)
        return mockk<Collection>(relaxed = true) {
            every { decks.cardCount(Consts.DEFAULT_DECK_ID, includeSubdecks = true) } returns 1
            every { sched } returns
                mockk<Scheduler> {
                    every { deckTree(includeCounts = false) } returns tree
                    every { customStudyDefaults(Consts.DEFAULT_DECK_ID) } returns response
                }
        }
    }
}
