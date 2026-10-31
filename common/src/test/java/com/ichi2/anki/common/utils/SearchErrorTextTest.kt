// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.utils

import org.junit.Test
import kotlin.test.assertEquals

class SearchErrorTextTest {
    @Test
    fun `ordinary text and unmatched delimiters are preserved`() {
        val text = "Error: <tag> &amp; a_b *c*\n  `unfinished <pre>"
        assertEquals(text, parseSearchError(text).renderText())
    }

    @Test
    fun `regex diagnostics preserve embedded closing tags`() {
        val code = "regex parse error:\n    (?i)</pre>[<b>&amp;`\n              ^\nerror: unclosed character class"
        val message = parseSearchError("<pre>$code</pre>")
        assertEquals(code, message.renderText())
        assertEquals(listOf(code), codeSpans(message))
    }

    @Test
    fun `malformed or unsupported code delimiters remain literal`() {
        val messages =
            listOf(
                "an `unclosed",
                "`a` and `unclosed",
                "``a``",
                "`a``b`",
                "`a` then ` `",
                "`a\nb`",
                "`a\rb`",
                "\\`a\\`",
            )
        for (text in messages) {
            val message = parseSearchError(text)
            assertEquals(text, message.renderText())
            assertEquals(emptyList(), codeSpans(message))
        }
    }

    @Test
    fun `inline code trims edge whitespace like Python Markdown`() {
        val message = parseSearchError("Use `  and  ` or ` <b>&amp; `.")
        assertEquals("Use and or <b>&amp;.", message.renderText())
        assertEquals(listOf("and", "<b>&amp;"), codeSpans(message))
    }

    private fun List<SearchErrorPart>.renderText(): String = joinToString("") { it.text }

    private fun codeSpans(parts: List<SearchErrorPart>): List<String> = parts.filter { it.isCode }.map { it.text }
}
