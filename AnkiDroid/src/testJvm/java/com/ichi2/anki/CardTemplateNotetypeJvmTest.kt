// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 Mike Hardy <mike@mikehardy.net>

package com.ichi2.anki

import com.ichi2.anki.libanki.NotetypeJson
import org.json.JSONObject
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import timber.log.Timber
import java.io.IOException
import kotlin.test.junit5.JUnit5Asserter.assertNotNull

class CardTemplateNotetypeJvmTest {
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
}
