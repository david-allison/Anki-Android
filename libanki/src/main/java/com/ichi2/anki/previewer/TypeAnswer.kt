// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.previewer

import com.ichi2.anki.cardviewer.TypeAnswerModifiers
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.Field
import com.ichi2.anki.libanki.keyboardLanguageHint
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.VisibleForTesting
import java.util.Locale

/**
 * Handles `type in the answer card` properties
 *
 * @see [combining]
 * @see [keyboardLanguageHint]
 * @see [noSuggest]
 * */
class TypeAnswer private constructor(
    private val text: String,
    /** whether combining characters should be compared. Defined by the presence of the
     *   `nc:` specifier in the type answer tag */
    val combining: Boolean,
    /**
     * Whether keyboard suggestions, swiping and autocorrect should be disabled (#10352).
     *
     * @see com.ichi2.anki.model.FieldFilters.NoSuggestFilter
     */
    val noSuggest: Boolean,
    private val field: Field,
    var expectedAnswer: String,
) {
    val font = field.font
    val fontSize = field.fontSize

    /** Optional field language used to select the keyboard language. */
    val keyboardLanguageHint: Locale? by lazy { field.keyboardLanguageHint }

    fun answerFilter(
        col: Collection,
        typedAnswer: String = "",
    ): String {
        val answerComparison = col.compareAnswer(expectedAnswer, provided = typedAnswer, combining = combining)

        @Language("HTML")
        val repl = """<div style="font-family: '$font'; font-size: ${fontSize}px">$answerComparison</div>"""
        return typeAnsRe.replace(text, Regex.escapeReplacement(repl))
    }

    companion object {
        /** removes `[[type:]]` tags from the given [text] */
        fun removeTags(text: String): String = typeAnsRe.replace(text, "")

        /**
         * @return a [TypeAnswer] instance if [text] contains a `[[type:Field]]` tag
         * with a valid field name, or null if not.
         *
         * ([Source](https://github.com/ankitects/anki/blob/8af63f81eb235b8d21df4e8eeaa6e02f46b3fbf6/qt/aqt/reviewer.py#L702))
         */
        fun getInstance(
            col: Collection,
            card: Card,
            text: String,
        ): TypeAnswer? {
            val match = typeAnsRe.find(text) ?: return null
            val rawField = match.groups[1]?.value ?: return null

            val modifiers = TypeAnswerModifiers.parse(rawField)
            val fields = card.noteType(col).fields
            val typeAnswerField = fields.firstOrNull { it.name == modifiers.fieldName } ?: return null
            val expectedAnswer = getExpectedTypeInAnswer(col, card, isCloze = modifiers.cloze, fieldName = modifiers.fieldName)

            return TypeAnswer(
                text = text,
                combining = modifiers.combining,
                noSuggest = modifiers.noSuggest,
                field = typeAnswerField,
                expectedAnswer = expectedAnswer,
            )
        }

        /**
         * @param isCloze whether the placeholder was a `cloze:` type filter
         * @param fieldName the name of the field in the card template
         */
        private fun getExpectedTypeInAnswer(
            col: Collection,
            card: Card,
            isCloze: Boolean,
            fieldName: String,
        ): String {
            val expected = card.note(col).getItem(fieldName)
            return if (isCloze) {
                val clozeIdx = card.ord + 1
                col.extractClozeForTyping(expected, clozeIdx)
            } else {
                expected
            }
        }
    }
}

@VisibleForTesting
val typeAnsRe = Regex("\\[\\[type:(.+?)]]")
