// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.noteeditor

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NoteDraftFieldsTest {
    @Test
    fun `changing field count preserves hidden values and visible edits`() {
        val draft = NoteDraftFields()
        assertEquals(listOf("front", "back"), draft.resize(listOf("front", "back", "extra"), 2))
        assertEquals(listOf("edited front", "", "extra"), draft.resize(listOf("edited front", ""), 3))
        assertEquals(emptyList(), draft.hiddenValues)
    }

    @Test
    fun `hidden draft text remains an unsaved change`() {
        val draft = NoteDraftFields().apply { baseline = listOf("", "", "") }
        draft.resize(listOf("", "", "unsaved draft text"), 2)

        assertTrue(draft.hasChanges(listOf("", "")))
    }

    @Test
    fun `unchanged hidden sticky fields are not unsaved changes`() {
        val draft = NoteDraftFields().apply { baseline = listOf("", "", "sticky text") }
        val visible = draft.resize(listOf("", "", "sticky text"), 2)
        draft.normalizeBaseline(2, true)
        assertFalse(draft.hasChanges(visible))

        val restored = draft.resize(visible, 3)
        draft.normalizeBaseline(3, true)

        assertEquals(listOf("", "", "sticky text"), restored)
        assertFalse(draft.hasChanges(restored))
    }

    @Test
    fun `sticky HTML line breaks are normalized when a type switch makes them visible`() {
        val draft = NoteDraftFields().apply { baseline = listOf("", "", "Hello<br>World") }
        val visible = draft.resize(listOf("", "", "Hello<br>World"), 2)
        draft.normalizeBaseline(2, true)
        assertEquals("Hello<br>World", draft.baseline[2])
        assertFalse(draft.hasChanges(visible))
        draft.resize(visible, 3)
        draft.normalizeBaseline(3, true)

        assertEquals(listOf("", "", "Hello${FieldText.newLine}World"), draft.baseline)
        assertFalse(draft.hasChanges(listOf("", "", "Hello${FieldText.newLine}World")))
    }

    @Test
    fun `cleared edits stay unchanged when a type switch adds a blank field`() {
        val draft = NoteDraftFields().apply { baseline = listOf("", "") }
        assertTrue(draft.hasChanges(listOf("typed", "")))
        assertFalse(draft.hasChanges(listOf("", "")))
        draft.resize(listOf("", ""), 3)
        draft.normalizeBaseline(3, true)

        assertFalse(draft.hasChanges(listOf("", "", "")))
    }

    @Test
    fun `clearing a saved or discarded draft prevents hidden values from leaking`() {
        val draft = NoteDraftFields()
        draft.resize(listOf("front", "back", "must not leak"), 2)

        draft.clearHiddenFields()

        assertEquals(listOf("", ""), draft.resize(listOf("", ""), 3))
        assertEquals(emptyList(), draft.hiddenValues)
    }

    @Test
    fun `disabling newline replacement preserves literal markup`() {
        assertEquals("a<br />b", FieldText.toEditor("a<br />b", false))
        assertEquals("a${FieldText.newLine}b", FieldText.toStored("a${FieldText.newLine}b", false))
        val draft = NoteDraftFields().apply { baseline = listOf("a<br>b") }
        draft.normalizeBaseline(1, false)
        assertFalse(draft.hasChanges(listOf("a<br>b")))
    }

    @Test
    fun `edited field comparison still detects changed content`() {
        assertTrue(FieldText.hasChanges(listOf("changed", "back"), listOf("front", "back")))
        assertTrue(FieldText.hasChanges(listOf("front"), listOf("front", "back")))
    }
}
