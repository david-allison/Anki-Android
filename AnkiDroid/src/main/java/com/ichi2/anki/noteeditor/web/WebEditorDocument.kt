// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.web

/** Field HTML and the clean baseline remain in the WebView until explicitly requested. */
data class WebEditorDocument(
    val sessionId: String,
    val generation: Int,
    val fields: List<WebEditorField>,
    val isCloze: Boolean = false,
)

data class WebEditorField(
    val name: String,
    val html: String,
    val fontName: String = "Arial",
    val fontSize: Int = 20,
    val rtl: Boolean = false,
    val languageTag: String? = null,
    val sticky: Boolean = false,
    val collapsed: Boolean = false,
    val sourceMode: Boolean = false,
)

data class WebEditorStatus(
    val revision: Int,
    val hasChanges: Boolean,
    val composing: Boolean = false,
    val hasSelection: Boolean = false,
)

data class WebEditorSnapshot(
    val sessionId: String,
    val generation: Int,
    val revision: Int,
    val fields: List<String>,
    val hasChanges: Boolean,
)

/** A conservative bookmark: edits to the destination field invalidate delayed commands. */
data class WebEditorTarget(
    val sessionId: String,
    val generation: Int,
    val field: Int,
    val revision: Int,
    val bookmark: String,
)

enum class WebEditorAction {
    BOLD,
    ITALIC,
    UNDERLINE,
    CLOZE_NEW,
    CLOZE_SAME,
    UNDO,
    REDO,
    INSERT_TEXT,
    INSERT_HTML,
    SOURCE_MODE,
}

data class WebEditorDraft(
    val document: WebEditorDocument,
    val baseline: List<String>,
    val hostStateJson: String,
)
