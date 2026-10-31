// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 Mike Hardy <mike@mikehardy.net>

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.libanki.Notetypes
import com.ichi2.testutils.EmptyApplication
import com.ichi2.testutils.parcelledCopy
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class CardTemplateNotetypeTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `parcelled editor state preserves template identities and deletions`() {
        val notetype =
            NotetypeJson(
                """{"id":1,"css":"😀 中文 é","tmpls":[{"ord":0,"name":"First"},{"ord":1,"name":"Second"},{"ord":2,"name":"Third"}]}""",
            )
        val edited = CardTemplateNotetype(notetype)
        edited.removeTemplate(1)
        edited.addNewTemplate(Notetypes.newTemplate("New"))
        edited.repositionTemplate(2, 0)

        val bundle = edited.toBundle(tempFolder.root).parcelledCopy(javaClass.classLoader)
        val restored = assertNotNull(CardTemplateNotetype.fromBundle(bundle))

        assertEquals(listOf("New", "First", "Third"), restored.notetype.templatesNames)
        assertEquals(listOf(null, 0, 2), (0 until restored.templateCount).map { restored.getOriginalOrdinal(it) })
        assertContentEquals(intArrayOf(1, 2), restored.getDeleteDbOrds(2))
        assertEquals("😀 中文 é", restored.css)
    }
}
