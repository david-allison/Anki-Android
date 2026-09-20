// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.config.ConfigKey
import com.ichi2.anki.common.destinations.NoteEditorDestination
import com.ichi2.anki.common.destinations.toIntent
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.noteeditor.getNoteEditorFragment
import com.ichi2.anki.noteeditor.openNoteEditorWithArgs
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Deck and note-type selection for Issue 20497.
 *
 * Anki keeps the open editor's selections separate from the selected study deck, and records
 * defaults when a note is added. In particular, a fresh Add window and a note-type change have
 * different fallback rules. Explicit launch validation and activity recreation also cover
 * Android-specific behavior.
 *
 * @see <a href="https://github.com/ankitects/anki/blob/754ce3a25f608010c0249e074e5d7fe95bda035f/qt/aqt/addcards_legacy.py#L91-L118">Upstream initialization</a>
 * @see <a href="https://github.com/ankitects/anki/blob/754ce3a25f608010c0249e074e5d7fe95bda035f/qt/aqt/addcards_legacy.py#L165-L171">Upstream note-type changes</a>
 * @see <a href="https://docs.ankiweb.net/preferences.html#editing">Upstream persistence policy</a>
 */
@RunWith(AndroidJUnit4::class)
class NoteEditorDeckSelectionTest : RobolectricTest() {
    private fun useCurrentDeck(current: Boolean) {
        ensureCollectionLoadIsSynchronous()
        col.config.setBool(ConfigKey.Bool.ADDING_DEFAULTS_TO_CURRENT_DECK, current)
    }

    private fun openAdd() = openNoteEditorWithArgs(NoteEditorFragment.addNoteArgs())

    private fun share(text: String) =
        openNoteEditorWithArgs(
            Bundle().apply { putString(Intent.EXTRA_TEXT, text) },
            Intent.ACTION_SEND,
        )

    private fun NoteEditorFragment.select(did: DeckId) {
        onDeckSelected(SelectableDeck.Deck(did, col.decks.name(did)))
    }

    @Test
    fun `successive shares use the study deck in current-deck mode`() =
        runTest {
            useCurrentDeck(true)
            val a = addDeck("Study A", setAsSelected = true)
            val b = addDeck("Add B")
            val first = share("first share")
            val activity = first.requireActivity()
            first.select(b)
            first.saveNote()
            advanceRobolectricLooper()
            assertTrue(activity.isFinishing)
            assertEquals(listOf(b), col.findCards("").map { col.getCard(it).did })
            assertEquals(a, col.decks.selected())
            assertEquals(a, share("second share").deckId)
        }

    @Test
    fun `successive shares remember the destination in note-type mode`() =
        runTest {
            useCurrentDeck(false)
            val a = addDeck("Study A", setAsSelected = true)
            val b = addDeck("Add B")
            val first = share("first share")
            val ntid = first.editorNote!!.noteTypeId
            val activity = first.requireActivity()
            first.select(b)
            first.saveNote()
            advanceRobolectricLooper()
            assertTrue(activity.isFinishing)
            assertEquals(listOf(b), col.findCards("").map { col.getCard(it).did })
            assertEquals(a, col.decks.selected())
            val second = share("second share")
            assertEquals(ntid, second.editorNote!!.noteTypeId)
            assertEquals(b, second.deckId)
        }

    @Test
    fun `explicit Default deck takes precedence over the remembered deck`() =
        runTest {
            useCurrentDeck(false)
            val b = addDeck("Remembered B", setAsSelected = true)
            col.addNote(col.newNote(col.notetypes.basic).apply { fields[0] = "saved" }, b)
            assertEquals(b, col.defaultsForAdding().deckId)
            val activity =
                startActivityNormallyOpenCollectionWithIntent(
                    NoteEditorActivity::class.java,
                    NoteEditorDestination.AddNote(1L).toIntent(),
                )
            assertEquals(1L, activity.getNoteEditorFragment().deckId)
        }

    // Preserve Android's existing launch validation. Anki's legacy desktop chooser instead uses
    // Default for an invalid explicit destination; this is not an upstream parity assertion.
    @Test
    fun `invalid explicit destination falls back to the remembered deck`() =
        runTest {
            useCurrentDeck(false)
            addDeck("Study", setAsSelected = true)
            val rememberedDeck = addDeck("Remembered")
            col.addNote(col.newNote(col.notetypes.basic).apply { fields[0] = "existing" }, rememberedDeck)
            val filteredDeck = addDynamicDeck("Filtered")
            val missingDeck = addDeck("Deleted")
            col.decks.remove(listOf(missingDeck))

            for (invalidDeck in listOf(filteredDeck, missingDeck)) {
                val activity =
                    startActivityNormallyOpenCollectionWithIntent(
                        NoteEditorActivity::class.java,
                        NoteEditorDestination.AddNote(invalidDeck).toIntent(),
                    )
                assertEquals(rememberedDeck, activity.getNoteEditorFragment().deckId)
                activity.finish()
            }
        }

    @Test
    fun `switching to a note type without history preserves the chosen deck`() =
        runTest {
            useCurrentDeck(false)
            addDeck("Study A", setAsSelected = true)
            val b = addDeck("Add B")
            val reversed = col.notetypes.basicAndReversed
            assertNull(col.defaultDeckForNoteType(reversed.id))
            val editor = openAdd()
            editor.select(b)
            editor.setCurrentlySelectedNoteType(reversed.id)
            advanceRobolectricLooper()
            assertEquals(reversed.id, editor.editorNote!!.noteTypeId)
            assertEquals(b, editor.deckId)
        }

    @Test
    fun `deleted remembered deck does not replace the editor destination on a note-type change`() =
        runTest {
            useCurrentDeck(false)
            addDeck("Study", setAsSelected = true)
            val destination = addDeck("Destination")
            val deletedDeck = addDeck("Deleted")
            val reversed = col.notetypes.basicAndReversed
            col.addNote(col.newNote(reversed).apply { fields[0] = "existing" }, deletedDeck)
            col.decks.remove(listOf(deletedDeck))
            col.notetypes.setCurrent(col.notetypes.basic)
            assertNull(col.defaultDeckForNoteType(reversed.id))

            val editor = openAdd()
            editor.select(destination)
            editor.setCurrentlySelectedNoteType(reversed.id)
            advanceRobolectricLooper()

            assertEquals(reversed.id, editor.editorNote!!.noteTypeId)
            assertEquals(destination, editor.deckId)
        }

    @Test
    fun `canceling a note-type change preserves the saved deck default`() =
        runTest {
            useCurrentDeck(true)
            val a = addDeck("Study A", setAsSelected = true)
            val basic = col.notetypes.basic
            col.addNote(col.newNote(basic).apply { fields[0] = "saved basic" }, a)
            val editor = openAdd()
            editor.setCurrentlySelectedNoteType(col.notetypes.basicAndReversed.id)
            advanceRobolectricLooper()
            editor.requireActivity().finish()
            assertEquals(basic.id, col.defaultsForAdding().notetypeId)
            assertEquals(1, col.noteCount())
            assertEquals(basic.id, openAdd().editorNote!!.noteTypeId)
        }

    @Test
    fun `initial deck and note type match backend defaults after a collection add`() =
        runTest {
            useCurrentDeck(false)
            val a = addDeck("Study A", setAsSelected = true)
            val b = addDeck("Add B")
            val editor = openAdd()
            editor.setCurrentlySelectedNoteType(col.notetypes.basicAndReversed.id)
            advanceRobolectricLooper()
            editor.setFieldValueFromUi(0, "reversed in A")
            editor.saveNote()
            advanceRobolectricLooper()
            editor.requireActivity().finish()
            val basic = col.notetypes.basic
            col.addNote(col.newNote(basic).apply { fields[0] = "basic in B via backend" }, b)
            assertEquals(a, col.decks.selected())
            val expected = col.defaultsForAdding()
            assertEquals(b, expected.deckId)
            assertEquals(basic.id, expected.notetypeId)
            val next = openAdd()
            assertEquals(expected.deckId, next.deckId)
            assertEquals(expected.notetypeId, next.editorNote!!.noteTypeId)
        }

    @Test
    fun `canceling a note-type change preserves the last added note type`() =
        runTest {
            useCurrentDeck(false)
            val studyDeck = addDeck("Study", setAsSelected = true)
            val rememberedDeck = addDeck("Remembered")
            val basic = col.notetypes.basic
            col.addNote(col.newNote(basic).apply { fields[0] = "saved" }, rememberedDeck)

            val editor = openAdd()
            editor.setCurrentlySelectedNoteType(col.notetypes.basicAndReversed.id)
            advanceRobolectricLooper()
            editor.requireActivity().finish()

            val next = openAdd()
            assertEquals(basic.id, next.editorNote!!.noteTypeId)
            assertEquals(rememberedDeck, next.deckId)
            assertEquals(studyDeck, col.decks.selected())
            assertEquals(1, col.noteCount())
        }

    @Test
    fun `saving retains the open editor selections when the study deck has another note type`() =
        runTest {
            useCurrentDeck(true)
            val studyDeck = addDeck("Study", setAsSelected = true)
            val destination = addDeck("Destination")
            val basic = col.notetypes.basic
            val reversed = col.notetypes.basicAndReversed
            col.addNote(col.newNote(basic).apply { fields[0] = "existing" }, studyDeck)

            val editor = openAdd()
            editor.select(destination)
            editor.setCurrentlySelectedNoteType(reversed.id)
            advanceRobolectricLooper()
            assertEquals(destination, editor.deckId)
            editor.setFieldValueFromUi(0, "new note")
            editor.saveNote()
            advanceRobolectricLooper()

            assertFalse(editor.requireActivity().isFinishing)
            assertEquals(destination, editor.deckId)
            assertEquals(reversed.id, editor.editorNote!!.noteTypeId)
            val defaults = col.defaultsForAdding()
            assertEquals(studyDeck, defaults.deckId)
            assertEquals(basic.id, defaults.notetypeId)
            assertEquals(basic.id, openAdd().editorNote!!.noteTypeId)
        }

    @Test
    fun `recreation preserves an unsaved note type and its fields`() =
        runTest {
            useCurrentDeck(true)
            val studyDeck = addDeck("Study", setAsSelected = true)
            val basic = col.notetypes.basic
            val cloze = col.notetypes.cloze
            col.addNote(col.newNote(basic).apply { fields[0] = "existing" }, studyDeck)
            val controller =
                startActivityControllerNormallyOpenCollectionWithIntent(
                    NoteEditorActivity::class.java,
                    NoteEditorDestination.AddNote().toIntent(),
                )
            val editor = controller.get().getNoteEditorFragment()
            editor.setCurrentlySelectedNoteType(cloze.id)
            advanceRobolectricLooper()
            editor.setFieldValueFromUi(0, "{{c1::unsaved cloze}}")

            controller.recreate()
            advanceRobolectricLooper()

            val restored = controller.get().getNoteEditorFragment()
            assertEquals(cloze.id, restored.editorNote!!.noteTypeId)
            assertEquals("{{c1::unsaved cloze}}", restored.currentFieldStrings[0])
            assertEquals(basic.id, col.defaultsForAdding().notetypeId)
            assertEquals(1, col.noteCount())
        }
}
