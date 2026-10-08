// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose.preview

import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.CardOrdinal
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.Consts.DEFAULT_DECK_ID
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.libanki.NoteId
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.libanki.clozeNumbersInNote

/** Current unsaved content. Rendering this value never writes a note or a card to the collection. */
data class EditorPreviewInput(
    val notetypeJson: String,
    val fields: List<String>,
    val tags: List<String>,
    val noteId: NoteId = 0,
    val deckId: DeckId = DEFAULT_DECK_ID,
)

internal data class PreviewCardChoice(
    val ordinal: CardOrdinal,
    val name: String,
)

internal data class RenderedEditorPreview(
    val card: Card,
    val choices: List<PreviewCardChoice>,
)

/** Creates a fresh note and note type for each render so a later edit cannot mutate the displayed card. */
internal fun Collection.renderEditorPreview(
    input: EditorPreviewInput,
    preferredOrdinal: CardOrdinal,
): RenderedEditorPreview {
    val notetype = NotetypeJson(input.notetypeJson)
    require(input.fields.size == notetype.fields.length())
    val backendNote =
        if (input.noteId == 0L) {
            backend.newNote(notetype.id)
        } else {
            backend
                .getNote(input.noteId)
                .toBuilder()
                .setNotetypeId(notetype.id)
                .build()
        }
    val note =
        Note(this, backendNote).apply {
            this.notetype = notetype
            fields = input.fields.toMutableList()
            tags = input.tags.toMutableList()
        }
    val choices =
        if (notetype.isCloze) {
            clozeNumbersInNote(note)
                .ifEmpty { listOf(1) }
                .map { number -> PreviewCardChoice(number - 1, tr.cardTemplatesCard(number)) }
        } else {
            notetype.templatesNames.mapIndexed { ordinal, name -> PreviewCardChoice(ordinal, name) }
        }
    val ordinal = choices.firstOrNull { it.ordinal == preferredOrdinal }?.ordinal ?: choices.first().ordinal
    // deckId configures native media playback. The backend's uncommitted renderer does not accept
    // a deck ID: {{Deck}}/{{Subdeck}} still use the saved card's deck, or "(Deck)" for a new note.
    return RenderedEditorPreview(
        card =
            note.ephemeralCard(
                col = this,
                ord = ordinal,
                customNoteType = notetype,
                fillEmpty = false,
                deckId = input.deckId,
            ),
        choices = choices,
    )
}
