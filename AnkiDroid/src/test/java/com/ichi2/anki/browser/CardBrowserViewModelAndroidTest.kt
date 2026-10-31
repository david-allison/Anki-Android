// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.NoteEditorFragment
import com.ichi2.anki.browser.CardBrowserViewModelTestBase.InitMode
import com.ichi2.anki.common.destinations.NoteEditorDestination
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.noteeditor.toIntent
import com.ichi2.testutils.EmptyApplication
import com.ichi2.testutils.IntentAssert
import com.ichi2.testutils.mockIt
import com.ichi2.testutils.parcelledCopy
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.not
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Android parcel and Intent integration remains alongside the JVM domain tests. */
@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class CardBrowserViewModelAndroidTest : JvmCardBrowserViewModelTestBase() {
    @Test
    fun `selection snapshot is saved and restored through Android parcelled state`() {
        val handle = SavedStateHandle()
        var selected: CardOrNoteId? = null
        runViewModelTest(savedStateHandle = handle, notes = 2, initMode = InitMode.NO_DELAY) {
            selectRowAtPosition(1)
            selected = selectedRows.single()
        }

        val saved = handle.savedStateProvider().saveState().parcelledCopy(javaClass.classLoader)
        val restored = SavedStateHandle.createHandle(saved, null)
        runViewModelTest(savedStateHandle = restored, initMode = InitMode.NO_DELAY) {
            assertEquals(listOf(selected), selectedRows.toList())
        }
    }

    @Test
    fun `empty selection is omitted from Android saved state`() {
        val handle = SavedStateHandle()
        runViewModelTest(savedStateHandle = handle, notes = 1, initMode = InitMode.NO_DELAY) {}

        val saved = handle.savedStateProvider().saveState().parcelledCopy(javaClass.classLoader)
        val restored = SavedStateHandle.createHandle(saved, null)
        assertNull(SavedStateHandleBrowserSelectionState(restored).restore())
    }

    @Test
    fun addCardDeckIsNotSetIfAllDecksSelectedAfterLoad() =
        runViewModelTest {
            addDeck("NotDefault")

            assertThat("All decks should not be selected", !hasSelectedAllDecks())

            setSelectedDeck(SelectableDeck.AllDecks)

            assertThat("All decks should be selected", hasSelectedAllDecks())

            val addIntent =
                NoteEditorDestination
                    .AddNoteFromCardBrowser(searchTerms = searchTerms, deckId = lastDeckId)
                    .toIntent(mockIt())
            IntentAssert.doesNotHaveExtra(addIntent.extras, NoteEditorFragment.EXTRA_DID)
        }
}
