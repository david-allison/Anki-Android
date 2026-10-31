// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.libanki

import com.ichi2.anki.common.utils.ext.AddingDefaultsMode
import com.ichi2.anki.common.utils.ext.addingDefaultsMode
import com.ichi2.anki.libanki.testutils.InMemoryAnkiTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class NoteEditorSelectionTest : InMemoryAnkiTest() {
    private fun deck(name: String) = col.decks.id(name)

    private fun rememberDeckForNoteType(
        deckId: DeckId,
        noteTypeId: NoteTypeId,
    ) {
        col.addNote(col.newNote(col.notetypes.get(noteTypeId)!!).apply { fields[0] = "existing" }, deckId)
    }

    @Test
    fun `a deleted shared destination falls back to the study deck`() {
        col.config.addingDefaultsMode = AddingDefaultsMode.USE_CURRENT_DECK
        val deleted = deck("Deleted")
        val study = deck("Study")
        col.decks.select(study)
        col.decks.remove(listOf(deleted))
        assertEquals(study, NoteEditorSelection.forAdding(col, sharedDeckId = deleted).deckId)
        assertEquals(study, col.decks.selected())
    }

    @Test
    fun `note-type defaults take precedence over the shared destination`() {
        val shared = deck("Shared")
        val remembered = deck("Remembered")
        val reversed = col.notetypes.basicAndReversed.id
        col.config.addingDefaultsMode = AddingDefaultsMode.DECIDE_BY_NOTE_TYPE
        rememberDeckForNoteType(remembered, reversed)
        assertEquals(NoteEditorSelection(remembered, reversed), NoteEditorSelection.forAdding(col, sharedDeckId = shared))
    }

    @Test
    fun `explicit Default precedes both shared and note-type destinations`() {
        val remembered = deck("Remembered")
        rememberDeckForNoteType(remembered, col.notetypes.basic.id)
        for (mode in AddingDefaultsMode.entries) {
            col.config.addingDefaultsMode = mode
            assertEquals(
                Consts.DEFAULT_DECK_ID,
                NoteEditorSelection.forAdding(col, launchDeckId = Consts.DEFAULT_DECK_ID, sharedDeckId = remembered).deckId,
            )
        }
    }

    @Test
    fun `invalid explicit destinations fall back to backend defaults`() {
        col.config.addingDefaultsMode = AddingDefaultsMode.DECIDE_BY_NOTE_TYPE
        val remembered = deck("Remembered")
        rememberDeckForNoteType(remembered, col.notetypes.basic.id)
        val filtered = col.decks.newFiltered("Filtered")
        val deleted = deck("Deleted")
        col.decks.remove(listOf(deleted))
        for (invalid in listOf(filtered, deleted)) {
            assertEquals(remembered, NoteEditorSelection.forAdding(col, launchDeckId = invalid).deckId)
        }
    }

    @Test
    fun `restored selections precede launch destinations and invalid note types use defaults`() {
        val restored = deck("Restored")
        val launch = deck("Launch")
        val noteType = col.notetypes.basicAndReversed.id
        assertEquals(NoteEditorSelection(restored, noteType), NoteEditorSelection.forAdding(col, restored, launch, noteType))
        assertEquals(col.defaultsForAdding().notetypeId, NoteEditorSelection.forAdding(col, currentNoteTypeId = -1).noteTypeId)
    }

    @Test
    fun `changing note type without history preserves the editor destination`() {
        col.config.addingDefaultsMode = AddingDefaultsMode.DECIDE_BY_NOTE_TYPE
        val destination = deck("Destination")
        val selection = NoteEditorSelection(destination, col.notetypes.basic.id)
        assertEquals(
            NoteEditorSelection(destination, col.notetypes.basicAndReversed.id),
            selection.changeNoteType(col, col.notetypes.basicAndReversed.id),
        )
    }

    @Test
    fun `missing and filtered remembered decks preserve the editor destination`() {
        col.config.addingDefaultsMode = AddingDefaultsMode.DECIDE_BY_NOTE_TYPE
        val destination = deck("Destination")
        val reversed = col.notetypes.basicAndReversed.id
        val deleted = deck("Deleted")
        rememberDeckForNoteType(deleted, reversed)
        col.decks.remove(listOf(deleted))
        val selection = NoteEditorSelection(destination, col.notetypes.basic.id)
        assertEquals(destination, selection.changeNoteType(col, reversed).deckId)
        rememberDeckForNoteType(col.decks.newFiltered("Filtered"), reversed)
        assertEquals(destination, selection.changeNoteType(col, reversed).deckId)
    }

    @Test
    fun `current-deck mode keeps the chosen deck despite note-type history`() {
        val study = deck("Study")
        col.decks.select(study)
        val destination = deck("Destination")
        val reversed = col.notetypes.basicAndReversed.id
        rememberDeckForNoteType(deck("Remembered"), reversed)
        col.config.addingDefaultsMode = AddingDefaultsMode.USE_CURRENT_DECK
        assertEquals(destination, NoteEditorSelection(destination, col.notetypes.basic.id).changeNoteType(col, reversed).deckId)
        assertEquals(study, col.decks.selected())
    }

    @Test
    fun `unknown or unchanged note types leave the selection intact`() {
        val selection = NoteEditorSelection(Consts.DEFAULT_DECK_ID, col.notetypes.basic.id)
        assertSame(selection, selection.changeNoteType(col, -1))
        assertSame(selection, selection.changeNoteType(col, selection.noteTypeId))
    }
}
