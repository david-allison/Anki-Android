// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 Mike Hardy <mike@mikehardy.net>

package com.ichi2.anki

import com.ichi2.anki.libanki.Notetypes
import com.ichi2.anki.libanki.utils.append
import com.ichi2.anki.libanki.utils.remove
import com.ichi2.testutils.JvmTest
import net.ankiweb.rsdroid.BackendException
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.junit5.JUnit5Asserter.assertNotNull
import kotlin.test.junit5.JUnit5Asserter.assertNull

class CardTemplateStateTest : JvmTest() {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `failed validation preserves a state saved before the save attempt`() =
        runTest {
            val original = col.notetypes.basicAndReversed
            val edited = CardTemplateNotetype(original.deepClone())
            edited.getTemplate(0).qfmt = "{{Missing}}"
            val state = edited.saveState(tempFolder.root)

            try {
                edited.saveToDatabase(tempFolder.root)
                Assert.fail("An unknown field must fail validation")
            } catch (_: BackendException) {
                // The saved state must remain usable after validation fails.
            }

            val restored = CardTemplateNotetype.fromSavedState(state)
            assertNotNull("Failed validation must preserve the saved editor state", restored)
            Assert.assertEquals(edited.notetype.toString(), restored!!.notetype.toString())
            restored.getTemplate(0).qfmt = original.templates[0].qfmt
            restored.saveToDatabase(tempFolder.root)
            assertNull("A successful save should clear the temporary file", CardTemplateNotetype.fromSavedState(state))
        }

    @Test
    fun `deletion checks preserve database ordinals across additions and deletions`() {
        val edited = CardTemplateNotetype(col.notetypes.basicAndReversed.deepClone())
        edited.appendTemplate("Third")
        edited.appendTemplate("Fourth")
        edited.deleteTemplate(1)
        edited.deleteTemplate(1)

        Assert.assertArrayEquals(intArrayOf(1, 0), edited.getDeleteDbOrds(0))
        Assert.assertEquals(0, edited.getOriginalOrdinal(0))
        assertNull("An unsaved addition must have no database ordinal", edited.getOriginalOrdinal(1))
        Assert.assertTrue(CardTemplateNotetype.isOrdinalPendingAdd(edited, 1))

        val restored = CardTemplateNotetype.fromSavedState(edited.saveState(tempFolder.root))!!
        Assert.assertArrayEquals(intArrayOf(1), restored.getDeleteDbOrds(1))
        restored.deleteTemplate(1)
        restored.appendTemplate("Replacement")
        Assert.assertArrayEquals(intArrayOf(1, 0), restored.getDeleteDbOrds(0))
        Assert.assertTrue(CardTemplateNotetype.isOrdinalPendingAdd(restored, 1))
    }

    @Test
    fun `mixed moves additions and deletions survive recreation and a failed save`() =
        runTest {
            val original = col.notetypes.basicAndReversed
            val note = addBasicAndReversedNote()
            val originalCards = note.cards().sortedBy { it.ord }
            val edited = CardTemplateNotetype(original.deepClone())
            edited.appendTemplate("New")
            edited.getTemplate(2).apply {
                qfmt = "{{Front}} new"
                afmt = "{{Back}}"
            }
            edited.repositionTemplate(2, 0)
            edited.deleteTemplate(2)
            edited.repositionTemplate(1, 0)
            Assert.assertArrayEquals(intArrayOf(1, 0), edited.getDeleteDbOrds(0))
            Assert.assertTrue(CardTemplateNotetype.isOrdinalPendingAdd(edited, 1))
            edited.getTemplate(0).qfmt = "{{Missing}}"

            val restored = CardTemplateNotetype.fromSavedState(edited.saveState(tempFolder.root))!!
            try {
                restored.saveToDatabase(tempFolder.root)
                Assert.fail("An unknown field must fail validation")
            } catch (_: BackendException) {
                Assert.assertEquals(originalCards.map { it.id }.toSet(), note.cids().toSet())
            }
            restored.getTemplate(0).qfmt = original.templates[0].qfmt
            restored.saveToDatabase(tempFolder.root)

            Assert.assertEquals(listOf("Card 1", "New"), col.notetypes.get(original.id)!!.templatesNames)
            Assert.assertEquals(originalCards[0].id, note.cards().single { it.ord == 0 }.id)
            Assert.assertFalse(note.cids().contains(originalCards[1].id))
            Assert.assertEquals(2, note.cids().size)
            col.undo()
            Assert.assertEquals(originalCards.map { it.id }.toSet(), note.cids().toSet())
        }

    private fun CardTemplateNotetype.appendTemplate(name: String) {
        notetype.templates.append(Notetypes.newTemplate(name))
        Notetypes._updateTemplOrds(notetype)
        addNewTemplate()
    }

    private fun CardTemplateNotetype.deleteTemplate(ordinal: Int) {
        removeTemplate(ordinal)
        notetype.templates.remove(getTemplate(ordinal))
        Notetypes._updateTemplOrds(notetype)
    }
}
