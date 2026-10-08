// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose.preview

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.testutils.JvmTest
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class EditorPreviewTest : JvmTest() {
    @Test
    fun `add preview renders unsaved fields and tags without creating a note`() {
        val notetype = col.notetypes.basic.deepClone()
        notetype.templates[0].qfmt = "{{Front}} / {{Tags}} / {{Deck}}"
        val deckId = col.decks.id("Preview deck")
        val input =
            EditorPreviewInput(
                notetypeJson = notetype.toString(),
                fields = listOf("<b>Unsaved front</b>", "Unsaved back"),
                tags = listOf("unsaved-tag"),
                deckId = deckId,
            )

        val preview = col.renderEditorPreview(input, preferredOrdinal = 0)
        val question = preview.card.question(col)

        assertTrue(question.contains("<b>Unsaved front</b>"))
        assertTrue(question.contains("unsaved-tag"))
        // Characterize the upstream limitation: the uncommitted renderer has no deck parameter.
        // The native card must still use the selected deck for its media configuration.
        assertTrue(question.contains("(Deck)"), question)
        assertTrue(preview.card.answer(col).contains("Unsaved back"))
        assertEquals(deckId, preview.card.did)
        assertEquals(0, col.noteCount())
        assertEquals(0, col.cardCount())
    }

    @Test
    fun `edit preview does not update saved fields tags or deck`() {
        val note = addBasicNote("Saved front", "Saved back")
        val originalCard = note.firstCard(col)
        val input =
            EditorPreviewInput(
                notetypeJson = note.notetype.toString(),
                fields = listOf("Edited front", "Edited back"),
                tags = listOf("edited-tag"),
                noteId = note.id,
                deckId = col.decks.id("Unsaved deck"),
            )

        val preview = col.renderEditorPreview(input, preferredOrdinal = 0)

        assertTrue(preview.card.question(col).contains("Edited front"))
        assertTrue(preview.card.answer(col).contains("Edited back"))
        assertEquals(listOf("Saved front", "Saved back"), col.getNote(note.id).fields)
        assertEquals(emptyList(), col.getNote(note.id).tags)
        assertEquals(originalCard.did, col.getCard(originalCard.id).did)
        assertEquals(1, col.noteCount())
        assertEquals(1, col.cardCount())
    }

    @Test
    fun `cloze updates retain actual selected ordinal and recover if it disappears`() {
        val input =
            EditorPreviewInput(
                notetypeJson = col.notetypes.cloze.toString(),
                fields = listOf("{{c4::four}} {{c7::seven}}", ""),
                tags = emptyList(),
            )

        val initial = col.renderEditorPreview(input, preferredOrdinal = 6)
        assertEquals(listOf(3, 6), initial.choices.map { it.ordinal })
        assertEquals(6, initial.card.ord)

        val updated = col.renderEditorPreview(input.copy(fields = listOf("{{c4::four}} {{c9::nine}}", "")), 6)
        assertEquals(listOf(3, 8), updated.choices.map { it.ordinal })
        assertEquals(3, updated.card.ord)

        val empty = col.renderEditorPreview(input.copy(fields = listOf("No clozes yet", "")), 3)
        assertEquals(0, empty.card.ord)
        assertEquals(0, col.noteCount())
    }

    @Test
    fun `changing note type previews mapped fields without modifying the existing note`() {
        val note = addBasicNote("Original front", "Original back")
        val input =
            EditorPreviewInput(
                notetypeJson = col.notetypes.cloze.toString(),
                fields = listOf("{{c2::New type}}", "Extra"),
                tags = emptyList(),
                noteId = note.id,
            )

        val preview = col.renderEditorPreview(input, preferredOrdinal = 1)

        assertEquals(1, preview.card.ord)
        assertTrue(preview.card.answer(col).contains("New type"))
        assertEquals(note.noteTypeId, col.getNote(note.id).noteTypeId)
        assertEquals(note.fields, col.getNote(note.id).fields)
        assertEquals(1, col.cardCount())
    }
}
