// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 Mike Hardy <mike@mikehardy.net>

package com.ichi2.anki

import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.libanki.Notetypes
import com.ichi2.anki.libanki.utils.append
import com.ichi2.anki.libanki.utils.remove
import com.ichi2.testutils.JvmTest
import org.json.JSONObject
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import timber.log.Timber
import java.io.IOException
import kotlin.test.junit5.JUnit5Asserter.assertNotNull

class CardTemplateNotetypeJvmTest : JvmTest() {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    @Throws(Exception::class)
    fun testTempNoteTypeStorage() {
        // Start off with clean state in the cache dir
        CardTemplateNotetype.clearTempNoteTypeFiles(tempFolder.root)

        // Make sure save / retrieve works
        val tempModelPath =
            CardTemplateNotetype.saveTempNoteType(
                tempFolder.root,
                NotetypeJson(
                    """{"foo": "bar"}""",
                ),
            )
        assertNotNull("Saving temp model unsuccessful", tempModelPath)
        val tempModel = CardTemplateNotetype.getTempNoteType(tempModelPath!!)
        assertNotNull("Temp model not read successfully", tempModel)
        Assert.assertEquals(JSONObject("{\"foo\": \"bar\"}").toString(), tempModel.toString())

        // Make sure clearing works
        Assert.assertEquals(1, CardTemplateNotetype.clearTempNoteTypeFiles(tempFolder.root).toLong())
        Timber.i("The following logged NoSuchFileException is an expected part of verifying a file delete.")
        try {
            CardTemplateNotetype.getTempNoteType(tempModelPath)
            Assert.fail("Should have caught an exception here because the file is missing")
        } catch (e: IOException) {
            // this is expected
        }
    }

    @Test
    fun `deletion checks retain the order of multiple original deletions`() {
        val notetype = col.notetypes.basicAndReversed.deepClone()
        for (name in listOf("Third", "Fourth")) {
            notetype.templates.append(Notetypes.newTemplate(name))
        }
        Notetypes._updateTemplOrds(notetype)
        val edited = CardTemplateNotetype(notetype)
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
                    val edited = CardTemplateNotetype(col.notetypes.get(original.id)!!.deepClone())
                    val expectedNames = original.templatesNames.toMutableList()
                    expectedNames.add(to, expectedNames.removeAt(from))
                    edited.repositionTemplate(from, to)
                    edited.saveToDatabase(tempFolder.root)

                    Assert.assertEquals(expectedNames, col.notetypes.get(original.id)!!.templatesNames)
                    Assert.assertEquals(originalCards, note.cards().associate { it.id to it.template().name })
                    col.undo()
                }
            }
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
