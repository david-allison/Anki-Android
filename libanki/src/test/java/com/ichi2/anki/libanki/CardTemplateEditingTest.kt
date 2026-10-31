// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 Mike Hardy <mike@mikehardy.net>

package com.ichi2.anki.libanki

import com.ichi2.anki.libanki.testutils.InMemoryAnkiTest
import net.ankiweb.rsdroid.BackendException
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardTemplateEditingTest : InMemoryAnkiTest() {
    @Test
    fun `new single card types reverse the front and back without changing the original`() {
        val original = col.notetypes.basic.deepClone()
        val edited = CardTemplateEditorModel(original)
        val proposed = edited.buildNewTemplate(0) { "Card $it" }

        assertEquals("Card 2", proposed.name)
        assertEquals("{{Back}}", proposed.qfmt)
        assertEquals("{{FrontSide}}\n\n<hr id=answer>\n\n{{Front}}", proposed.afmt)
        assertEquals(1, proposed.ord)
        assertEquals(1, edited.templateCount)
        assertEquals(col.notetypes.basic.toString(), original.toString())
    }

    @Test
    fun `new names skip existing names and copy the selected template`() {
        val edited = CardTemplateEditorModel(col.notetypes.basicAndReversed.deepClone())
        edited.getTemplate(0).name = "Card 3"
        val proposed = edited.buildNewTemplate(1) { "Card $it" }

        assertEquals("Card 4", proposed.name)
        assertEquals(edited.getTemplate(1).qfmt, proposed.qfmt)
        assertEquals(edited.getTemplate(1).afmt, proposed.afmt)
    }

    @Test
    fun `adding from an unsaved optional reverse template creates no cards`() {
        val noteType = col.notetypes.byName("Basic (optional reversed card)")!!
        val note =
            col.newNote(noteType).apply {
                setField(0, "Front")
                setField(1, "Back")
            }
        col.addNote(note, Consts.DEFAULT_DECK_ID)
        val edited = CardTemplateEditorModel(noteType.deepClone())
        val first = edited.buildNewTemplate(1) { "Card $it" }
        assertEquals(0, col.countCardsCreatedBy(edited.notetype, first))
        edited.addNewTemplate(first)
        first.qfmt = "{{#Add Reverse}}{{Front}} {{Back}}{{/Add Reverse}}"
        val second = edited.buildNewTemplate(2) { "Card $it" }
        assertEquals(0, col.countCardsCreatedBy(edited.notetype, second))
        edited.addNewTemplate(second)
        second.qfmt = "{{#Add Reverse}}{{Back}} {{Front}}{{/Add Reverse}}"
        assertNull(edited.getOriginalOrdinal(2))
        assertNull(edited.getOriginalOrdinal(3))

        col.notetypes.updateDict(edited.notetypeForSaving())

        assertEquals(
            4,
            col.notetypes
                .get(noteType.id)!!
                .templates
                .length(),
        )
        assertEquals(1, note.cids().size)
    }

    @Test
    fun `pending deletions are checked together and unsaved additions are independently removable`() {
        val note = addBasicAndReversedNote()
        val edited = CardTemplateEditorModel(col.notetypes.basicAndReversed.deepClone())
        assertFalse(edited.deletionWouldOrphanNote(col, 0))
        assertFalse(edited.deletionWouldOrphanNote(col, 1))
        edited.removeTemplate(0)
        assertTrue(edited.deletionWouldOrphanNote(col, 0))
        edited.addNewTemplate(edited.buildNewTemplate(0) { "Card $it" })
        assertFalse(edited.deletionWouldOrphanNote(col, 1))
        edited.removeTemplate(1)

        assertEquals(1, edited.templateCount)
        assertEquals(1, edited.getOriginalOrdinal(0))
        assertEquals(2, note.cids().size, "Unsaved edits leave the database unchanged")
    }

    @Test
    fun `selective generation prevents deleting the only generated card type`() {
        val noteType = col.notetypes.byName("Basic (optional reversed card)")!!
        val note =
            col.newNote(noteType).apply {
                setField(0, "Front")
                setField(1, "Back")
            }
        col.addNote(note, Consts.DEFAULT_DECK_ID)
        val edited = CardTemplateEditorModel(noteType.deepClone())
        assertTrue(edited.deletionWouldOrphanNote(col, 0))
        assertFalse(edited.deletionWouldOrphanNote(col, 1))

        note.setField(2, "yes")
        col.updateNote(note)

        assertFalse(edited.deletionWouldOrphanNote(col, 0))
        assertFalse(edited.deletionWouldOrphanNote(col, 1))
    }

    @Test
    fun `a failed save does not add card types`() {
        addBasicNote()
        val original = col.notetypes.basic
        val edited = CardTemplateEditorModel(original.deepClone())
        edited.addNewTemplate(edited.buildNewTemplate(0) { "Card $it" })
        edited.addNewTemplate(edited.buildNewTemplate(1) { "Card $it" })
        assertEquals(edited.getTemplate(1).qfmt, edited.getTemplate(2).qfmt)

        assertFailsWith<BackendException> { col.notetypes.updateDict(edited.notetypeForSaving()) }
        assertEquals(original.templatesNames, col.notetypes.get(original.id)!!.templatesNames)
        assertNull(edited.getOriginalOrdinal(1))
        assertNull(edited.getOriginalOrdinal(2))
        edited.getTemplate(2).qfmt = "{{Back}} {{Front}}"
        col.notetypes.updateDict(edited.notetypeForSaving())

        assertEquals(edited.notetype.templatesNames, col.notetypes.get(original.id)!!.templatesNames)
    }

    @Test
    fun `deleting the first card type keeps the cards of the second`() {
        val note = addBasicAndReversedNote()
        val reverseCardId = note.cards().single { it.ord == 1 }.id
        val edited = CardTemplateEditorModel(col.notetypes.basicAndReversed.deepClone())
        edited.removeTemplate(0)

        col.notetypes.updateDict(edited.notetypeForSaving())

        assertEquals(listOf(reverseCardId), note.cids())
    }

    @Test
    fun `mixed additions and original deletions preserve the remaining cards when saved`() {
        val note = addBasicAndReversedNote()
        val edited = CardTemplateEditorModel(col.notetypes.basicAndReversed.deepClone())
        edited.addNewTemplate(edited.buildNewTemplate(0) { "Card $it" }.apply { qfmt += " third" })
        col.notetypes.updateDict(edited.notetypeForSaving())
        val thirdCardId = note.cards().single { it.ord == 2 }.id
        val reloaded = CardTemplateEditorModel(col.notetypes.get(edited.noteTypeId)!!.deepClone())
        reloaded.addNewTemplate(reloaded.buildNewTemplate(2) { "Card $it" }.apply { qfmt += " fourth" })
        assertFalse(reloaded.deletionWouldOrphanNote(col, 0))
        reloaded.removeTemplate(0)
        assertFalse(reloaded.deletionWouldOrphanNote(col, 0))
        reloaded.removeTemplate(0)
        assertEquals(3, note.cids().size)

        col.notetypes.updateDict(reloaded.notetypeForSaving())

        assertEquals(2, note.cids().size)
        assertEquals(thirdCardId, note.cards().single { it.ord == 0 }.id)
        assertEquals(listOf("Card 3", "Card 4"), col.notetypes.get(edited.noteTypeId)!!.templatesNames)
    }
}
