// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.testutils.EmptyApplication
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class NotetypeFileTest {
    @get:Rule
    val tempDirectory = TemporaryFolder()

    @Test
    @Config(shadows = [RejectWholeDocumentParsing::class])
    fun `preview snapshot is parsed without a whole document string`() {
        val file = tempDirectory.newFile().apply { writeText("""{"css":".card {}"}""") }

        assertEquals(".card {}", NotetypeFile(file.path).getNotetype().css)
    }

    @Test
    @Config(shadows = [RejectWholeDocumentParsing::class])
    fun `editor snapshot is parsed without a whole document string`() {
        val file = tempDirectory.newFile().apply { writeText("""{"css":".card {}"}""") }

        assertEquals(".card {}", CardTemplateNotetype.getTempNoteType(file.path).css)
    }

    @Implements(JSONObject::class)
    class RejectWholeDocumentParsing {
        @Implementation
        @Suppress("ktlint:standard:function-naming") // Robolectric's constructor hook.
        fun __constructor__(json: String): Unit = error("Snapshot reading must not parse a whole document string")
    }
}
