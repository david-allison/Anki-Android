// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 Mike Hardy <mike@mikehardy.net>

package com.ichi2.anki.libanki

import com.ichi2.anki.libanki.testutils.InMemoryAnkiTest
import com.ichi2.anki.libanki.utils.append
import org.junit.Assert
import org.junit.Test

class CardTemplateEditorModelTest : InMemoryAnkiTest() {
    @Test
    fun `deletion checks retain the order of multiple original deletions`() {
        val notetype = col.notetypes.basicAndReversed.deepClone()
        for (name in listOf("Third", "Fourth")) {
            notetype.templates.append(Notetypes.newTemplate(name))
        }
        Notetypes._updateTemplOrds(notetype)
        val edited = CardTemplateEditorModel(notetype)
        edited.deleteTemplate(1)
        edited.deleteTemplate(0)

        Assert.assertArrayEquals(intArrayOf(1, 0, 2), edited.getDeleteDbOrds(0))
        Assert.assertEquals(2, edited.getOriginalOrdinal(0))
        Assert.assertEquals(3, edited.getOriginalOrdinal(1))
    }

    @Test
    fun `all reposition destinations preserve the cards belonging to each template`() =
        runTest {
            val original = col.notetypes.basicAndReversed
            col.notetypes.addTemplate(
                original,
                Notetypes.newTemplate("Third").apply {
                    qfmt = "{{Front}} third"
                    afmt = "{{Back}}"
                },
            )
            val note = addBasicAndReversedNote()
            val originalCards = note.cards().associate { it.id to it.template().name }
            for (from in 0..2) {
                for (to in 0..2) {
                    if (from == to) continue
                    val edited = CardTemplateEditorModel(col.notetypes.get(original.id)!!.deepClone())
                    val expectedNames = original.templatesNames.toMutableList()
                    expectedNames.add(to, expectedNames.removeAt(from))
                    edited.repositionTemplate(from, to)
                    col.notetypes.updateDict(edited.notetypeForSaving())

                    Assert.assertEquals(expectedNames, col.notetypes.get(original.id)!!.templatesNames)
                    Assert.assertEquals(originalCards, note.cards().associate { it.id to it.template().name })
                    col.undo()
                }
            }
        }

    private fun CardTemplateEditorModel.appendTemplate(name: String) {
        addNewTemplate(Notetypes.newTemplate(name))
    }

    private fun CardTemplateEditorModel.deleteTemplate(ordinal: Int) {
        removeTemplate(ordinal)
    }
}
