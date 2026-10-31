// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.destinations.NoteEditorDestination
import com.ichi2.anki.common.destinations.toIntent
import com.ichi2.anki.common.utils.ext.AddingDefaultsMode
import com.ichi2.anki.common.utils.ext.addingDefaultsMode
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.noteeditor.getNoteEditorFragment
import com.ichi2.anki.noteeditor.openNoteEditorWithArgs
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class NoteEditorDeckSelectionTest : RobolectricTest() {
    private fun openAdd(deckId: DeckId? = null) =
        startRegularActivity<NoteEditorActivity>(NoteEditorDestination.AddNote(deckId).toIntent()).getNoteEditorFragment()

    private fun useAddingDefaults(mode: AddingDefaultsMode) {
        ensureCollectionLoadIsSynchronous()
        col.config.addingDefaultsMode = mode
    }

    private fun share(
        text: String,
        deckId: DeckId? = null,
    ) = openNoteEditorWithArgs(
        Bundle().apply {
            putString(Intent.EXTRA_TEXT, text)
            deckId?.let { putLong(NoteEditorFragment.EXTRA_DID, it) }
        },
        Intent.ACTION_SEND,
    )

    private fun NoteEditorFragment.select(did: DeckId) {
        onDeckSelected(SelectableDeck.Deck(did, col.decks.name(did)))
    }

    @Test
    fun `successive shares remember the destination without changing the study deck`() =
        runTest {
            useAddingDefaults(AddingDefaultsMode.USE_CURRENT_DECK)
            val a = addDeck("Study A", setAsSelected = true)
            val b = addDeck("Add B")
            val first = share("first share")
            assertEquals(a, first.deckId)
            val activity = first.requireActivity()
            first.select(b)
            first.saveNote()
            advanceRobolectricLooper()
            assertTrue(activity.isFinishing)
            assertEquals(listOf(b), col.findCards("").map { col.getCard(it).did })
            assertEquals(a, col.decks.selected())
            assertEquals(b, share("second share").deckId)
            assertEquals(a, openAdd().deckId)
        }

    @Test
    fun `successive shares remember the destination in note-type mode`() =
        runTest {
            useAddingDefaults(AddingDefaultsMode.DECIDE_BY_NOTE_TYPE)
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

    private suspend fun saveSharedNote(deckId: DeckId) {
        val editor = share("saved share")
        val activity = editor.requireActivity()
        editor.select(deckId)
        editor.saveNote()
        advanceRobolectricLooper()
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `in-app additions do not replace the shared destination`() =
        runTest {
            useAddingDefaults(AddingDefaultsMode.USE_CURRENT_DECK)
            val study = addDeck("Study", setAsSelected = true)
            val shared = addDeck("Shared")
            val inApp = addDeck("In app")
            saveSharedNote(shared)

            val editor = openAdd(inApp)
            editor.setFieldValueFromUi(0, "added in app")
            editor.saveNote()
            advanceRobolectricLooper()

            assertEquals(2, col.noteCount())
            assertEquals(shared, share("next share").deckId)
            assertEquals(study, col.decks.selected())
        }

    @Test
    fun `canceling a share does not replace the saved destination`() =
        runTest {
            useAddingDefaults(AddingDefaultsMode.USE_CURRENT_DECK)
            addDeck("Study", setAsSelected = true)
            val saved = addDeck("Saved")
            val canceled = addDeck("Canceled")
            saveSharedNote(saved)

            val editor = share("canceled share")
            editor.select(canceled)
            editor.requireActivity().finish()

            assertEquals(1, col.noteCount())
            assertEquals(saved, share("next share").deckId)
        }

    @Test
    fun `a failed share does not replace the saved destination`() =
        runTest {
            useAddingDefaults(AddingDefaultsMode.USE_CURRENT_DECK)
            addDeck("Study", setAsSelected = true)
            val saved = addDeck("Saved")
            val failed = addDeck("Failed")
            saveSharedNote(saved)

            val editor = share("")
            editor.select(failed)
            editor.saveNote()
            advanceRobolectricLooper()
            assertNotNull(editor.addNoteErrorMessage)
            assertFalse(editor.requireActivity().isFinishing)
            editor.requireActivity().finish()

            assertEquals(1, col.noteCount())
            assertEquals(saved, share("next share").deckId)
        }

    @Test
    fun `selected text uses the last shared destination`() =
        runTest {
            useAddingDefaults(AddingDefaultsMode.USE_CURRENT_DECK)
            addDeck("Study", setAsSelected = true)
            val shared = addDeck("Shared")
            saveSharedNote(shared)

            val editor =
                openNoteEditorWithArgs(
                    Bundle().apply { putString(Intent.EXTRA_PROCESS_TEXT, "selected text") },
                    Intent.ACTION_PROCESS_TEXT,
                )
            assertEquals(shared, editor.deckId)
        }

    @Test
    fun `canceling a note-type change preserves the saved deck default`() =
        runTest {
            useAddingDefaults(AddingDefaultsMode.USE_CURRENT_DECK)
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
            useAddingDefaults(AddingDefaultsMode.DECIDE_BY_NOTE_TYPE)
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
            useAddingDefaults(AddingDefaultsMode.DECIDE_BY_NOTE_TYPE)
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
            useAddingDefaults(AddingDefaultsMode.USE_CURRENT_DECK)
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
            useAddingDefaults(AddingDefaultsMode.USE_CURRENT_DECK)
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

    @Test
    fun `restored selection takes precedence over the original destination`() =
        runTest {
            useAddingDefaults(AddingDefaultsMode.USE_CURRENT_DECK)
            val a = addDeck("Original A", setAsSelected = true)
            val b = addDeck("Chosen B")
            val controller =
                startActivityControllerNormallyOpenCollectionWithIntent(
                    NoteEditorActivity::class.java,
                    NoteEditorDestination.AddNote(a).toIntent(),
                )
            controller.get().getNoteEditorFragment().select(b)
            controller.recreate()
            advanceRobolectricLooper()
            assertEquals(b, controller.get().getNoteEditorFragment().deckId)
        }
}
