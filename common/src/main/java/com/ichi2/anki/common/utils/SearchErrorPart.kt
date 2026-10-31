// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.utils

/** A literal text fragment, with optional code styling applied by the platform. */
data class SearchErrorPart(
    val text: String,
    val isCode: Boolean = false,
)

/**
 * Parses the backend's search errors: single-backtick code spans, or an entire regex diagnostic
 * wrapped in `<pre>`. Unsupported code delimiters leave the entire message literal.
 *
 * Desktop converts search-syntax errors with Python-Markdown, whose code spans trim edge whitespace:
 * https://github.com/Python-Markdown/markdown/blob/3.10.3/markdown/inlinepatterns.py#L394-L413
 * Regex diagnostics contain raw user input, so remove only the outer wrapper, without parsing HTML:
 * https://github.com/ankitects/anki/blob/fe4f9e039a4e283b71e611fc1ffb87029b1cdc05/rslib/src/error/mod.rs#L161
 */
fun parseSearchError(message: String): List<SearchErrorPart> {
    if (message.startsWith("<pre>") && message.endsWith("</pre>")) {
        return listOf(SearchErrorPart(message.removeSurrounding("<pre>", "</pre>"), isCode = true))
    }

    val parts = message.split('`')
    if (!supportsInlineCode(message, parts)) return listOf(SearchErrorPart(message))
    return parts.mapIndexed { index, part ->
        if (index % 2 == 1) SearchErrorPart(part.trim(), isCode = true) else SearchErrorPart(part)
    }
}

private fun supportsInlineCode(
    message: String,
    parts: List<String>,
): Boolean {
    if (parts.size % 2 == 0 || "``" in message) return false

    // Splitting on backticks alternates prose and code, starting and ending with prose.
    for (codeIndex in 1 until parts.size step 2) {
        val code = parts[codeIndex]
        if (code.isBlank() || '\n' in code || '\r' in code) return false

        val precedingText = parts[codeIndex - 1]
        if (precedingText.endsWith('\\')) return false
    }
    return true
}
