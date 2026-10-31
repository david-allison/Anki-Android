// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.noteeditor

/** Draft values that survive temporarily switching to a note type with fewer fields. */
class NoteDraftFields {
    var hiddenValues: List<String> = emptyList()
    var baseline: List<String> = emptyList()

    fun allValues(visibleValues: List<String>): List<String> = visibleValues + hiddenValues

    /** Return values to retain in the new views, keeping any hidden tail for a later type switch. */
    fun resize(
        visibleValues: List<String>,
        fieldCount: Int,
    ): List<String> {
        val values = allValues(visibleValues)
        hiddenValues = values.drop(fieldCount)
        return values.take(fieldCount)
    }

    fun clearHiddenFields() {
        hiddenValues = emptyList()
    }

    fun hasChanges(visibleValues: List<String>): Boolean = allValues(visibleValues) != baseline

    /** Only visible fields pass through the editor's HTML line-break conversion. */
    fun normalizeBaseline(
        fieldCount: Int,
        replaceNewlines: Boolean,
    ) {
        baseline =
            List(maxOf(fieldCount, baseline.size)) { i ->
                val old = baseline.getOrElse(i) { "" }
                if (i < fieldCount) FieldText.toEditor(old, replaceNewlines) else old
            }
    }
}

/** Field text conversion and comparison shared by editor views, draft state and note persistence. */
object FieldText {
    val newLine: String = System.getProperty("line.separator")!!
    private val htmlLineBreak = "<br(\\s*/*)>".toRegex()

    fun toEditor(
        content: String,
        replaceNewlines: Boolean,
    ): String = if (replaceNewlines) content.replace(htmlLineBreak, newLine) else content

    fun toStored(
        content: String,
        replaceNewlines: Boolean,
    ): String = if (replaceNewlines) content.replace(newLine, "<br>") else content

    fun hasChanges(
        current: List<String>,
        original: List<String>,
    ): Boolean {
        fun normalize(value: String) = toStored(value, true).replace(htmlLineBreak, "<br>")
        return current.map(::normalize) != original.map(::normalize)
    }
}
