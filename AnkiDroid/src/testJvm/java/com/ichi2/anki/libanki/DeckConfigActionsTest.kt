// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Arthur Milchior <arthur@milchior.fr>

package com.ichi2.anki.libanki

import com.ichi2.anki.reviewer.AutomaticAnswerAction
import com.ichi2.anki.reviewer.AutomaticAnswerAction.Companion.answerAction
import com.ichi2.anki.ui.windows.reviewer.autoadvance.QuestionAction
import com.ichi2.anki.ui.windows.reviewer.autoadvance.QuestionAction.Companion.questionAction
import org.junit.Test
import kotlin.test.assertEquals

class DeckConfigActionsTest {
    private val dc = DeckConfig("{}")

    @Test
    fun testAnswerAction() {
        val dc = DeckConfig("""{"answerAction": ${AutomaticAnswerAction.ANSWER_AGAIN.configValue}}""")
        assertEquals(AutomaticAnswerAction.ANSWER_AGAIN, dc.answerAction)
        dc.removeAnswerAction()
        assertEquals(AutomaticAnswerAction.BURY_CARD, dc.answerAction)
        val dcError = DeckConfig("""{"answerAction": 42}""")
        assertEquals(AutomaticAnswerAction.BURY_CARD, dcError.answerAction)
    }

    @Test
    fun testQuestionAction() {
        assertEquals(QuestionAction.SHOW_ANSWER, dc.questionAction)
        val dc = DeckConfig(""" {"questionAction": 1} """)
        assertEquals(QuestionAction.SHOW_REMINDER, dc.questionAction)
    }
}
