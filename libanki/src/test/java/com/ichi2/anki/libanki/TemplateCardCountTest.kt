// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.libanki

import com.ichi2.anki.libanki.testutils.InMemoryAnkiTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration

class TemplateCardCountTest : InMemoryAnkiTest() {
    @Test
    fun `counting falls back to the note count when it times out`() {
        val noteType = col.notetypes.byName("Basic (optional reversed card)")!!
        val note = col.newNote(noteType)
        note.setField(0, "Front")
        note.setField(1, "Back")
        col.addNote(note, Consts.DEFAULT_DECK_ID)
        val reverseTemplate = noteType.templates[1]
        var timedOutNoteCount: Int? = null

        assertEquals(0, col.countCardsCreatedBy(noteType, reverseTemplate))
        assertEquals(1, col.countCardsCreatedBy(noteType, reverseTemplate, timeout = Duration.ZERO) { timedOutNoteCount = it })
        assertEquals(1, timedOutNoteCount)
    }

    @Test
    fun `a broken template creates no cards`() {
        addBasicAndReversedNote()
        val noteType = col.notetypes.basicAndReversed
        val proposed = noteType.templates[0].deepClone().apply { qfmt = "{{#Front}}" }

        assertEquals(0, col.countCardsCreatedBy(noteType, proposed))
    }

    @Test
    fun `card number conditions use generation semantics rather than preview ordinals`() {
        addBasicAndReversedNote()
        val noteType = col.notetypes.basicAndReversed
        val proposed =
            noteType.templates[0].deepClone().apply {
                qfmt = "{{#c3}}{{Front}}{{/c3}}"
                setOrd(2)
            }

        assertEquals(0, col.countCardsCreatedBy(noteType, proposed))
    }
}
