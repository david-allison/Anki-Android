// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.noteeditor

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@RunWith(Parameterized::class)
class FieldTextLineBreakTest(
    private val stored: String,
) {
    @Test
    fun `line break display conversion does not change the note`() {
        val visible = "hello${FieldText.newLine}world"
        assertEquals(visible, FieldText.toEditor(stored, true))
        assertFalse(FieldText.hasChanges(listOf(visible), listOf(stored)))
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun lineBreaks(): List<String> = listOf("hello${FieldText.newLine}world", "hello<br>world", "hello<br />world")
    }
}
