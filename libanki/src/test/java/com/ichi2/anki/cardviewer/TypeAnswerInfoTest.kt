// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.cardviewer

import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.keyboardLanguageHint
import com.ichi2.anki.libanki.testutils.InMemoryAnkiTest
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TypeAnswerInfoTest : InMemoryAnkiTest() {
    @Test
    fun `answer info reads field content font and keyboard language from the collection`() {
        val card = cardWithPlaceholder("Back")
        val noteType = card.noteType(col)
        noteType.fields[1].apply {
            font = "Example Font"
            fontSize = 27
            keyboardLanguageHint = Locale.UK
        }
        col.notetypes.save(noteType)
        val answer = TypeAnswer(useInputTag = true, autoFocus = true)

        answer.updateInfo(col, card) { error("Unexpected warning for $it") }

        assertEquals("Back", answer.correct)
        assertEquals("Example Font", answer.font)
        assertEquals(27, answer.size)
        assertEquals(Locale.UK, answer.languageHint)
        assertTrue(answer.filterQuestion(card.question(col)).contains("font-size: 27px"))
    }

    @Test
    fun `unknown field warnings use the supplied resource adapter`() {
        val answer = TypeAnswer(useInputTag = false, autoFocus = false)
        val card = cardWithPlaceholder("Missing")
        answer.updateInfo(col, card) { "Unknown field: $it" }

        assertNull(answer.correct)
        assertEquals("Unknown field: Missing", answer.warning)
        assertTrue(answer.filterQuestion(card.question(col)).contains("Unknown field: Missing"))
    }

    @Test
    fun `unknown cloze fields use the backend empty front warning`() {
        val answer = TypeAnswer(useInputTag = false, autoFocus = false)
        answer.updateInfo(col, cardWithPlaceholder("cloze:Missing")) { error("Cloze warnings use backend translations") }
        assertEquals(col.tr.cardTemplateRenderingEmptyFront(), answer.warning)
    }

    @Test
    fun `clearing a keyboard hint removes its persisted locale`() {
        val field = col.notetypes.basic.fields[0]
        field.keyboardLanguageHint = Locale.JAPAN
        assertEquals("ja-JP", field.jsonObject.getString("ad-hint-locale"))
        field.keyboardLanguageHint = null
        assertNull(field.keyboardLanguageHint)
    }

    private fun cardWithPlaceholder(field: String): Card {
        val card = addBasicNote().firstCard()
        val noteType = card.noteType(col)
        noteType.templates[0].qfmt = "{{Front}}[[type:$field]]"
        col.notetypes.save(noteType)
        return card
    }
}
