// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.libanki

import com.ichi2.anki.common.utils.ext.AddingDefaultsMode
import com.ichi2.anki.common.utils.ext.addingDefaultsMode

/** Deck and note type selected together when opening the note editor. */
data class NoteEditorSelection(
    val deckId: DeckId,
    val noteTypeId: NoteTypeId,
) {
    /** Preserve the editor's destination when the new note type has no usable remembered deck. */
    fun changeNoteType(
        col: Collection,
        newId: NoteTypeId,
    ): NoteEditorSelection {
        if (newId == noteTypeId || col.notetypes.get(newId) == null) return this
        return NoteEditorSelection(col.defaultDeckForNoteType(newId) ?: deckId, newId)
    }

    companion object {
        /**
         * Restored selections precede explicit launch destinations. Sharing has its own remembered
         * destination in current-deck mode; neither opening nor changing the editor selects a study deck.
         */
        fun forAdding(
            col: Collection,
            currentDeckId: DeckId = 0,
            launchDeckId: DeckId = 0,
            currentNoteTypeId: NoteTypeId? = null,
            sharedDeckId: DeckId = 0,
        ): NoteEditorSelection {
            val defaults = col.defaultsForAdding()
            val noteType = currentNoteTypeId?.takeIf { col.notetypes.get(it) != null } ?: defaults.notetypeId
            var deck = currentDeckId.takeUnless { it == 0L } ?: launchDeckId
            if (deck == 0L && col.config.addingDefaultsMode == AddingDefaultsMode.USE_CURRENT_DECK) {
                deck = sharedDeckId
            }
            val existingDeck = col.decks.getLegacy(deck)
            if (existingDeck == null || existingDeck.isFiltered) deck = defaults.deckId
            return NoteEditorSelection(deck, noteType)
        }
    }
}
