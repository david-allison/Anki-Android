// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.web

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** Field HTML and the clean baseline remain in the WebView until explicitly requested. */
data class WebEditorDocument(
    val sessionId: String,
    val generation: Int,
    val fields: List<WebEditorField>,
    val isCloze: Boolean = false,
    val settings: WebEditorSettings = WebEditorSettings(),
    val baselineFieldMap: List<Int>? = null,
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
    val quickActions: List<WebEditorFieldAction> = emptyList(),
)

/** Font, size and paragraph direction; these never change the field's HTML. */
data class WebEditorFieldTypography(
    val fontName: String,
    val fontSize: Int,
    val rtl: Boolean = false,
)

/** Host-defined controls; these never become part of the field HTML. */
data class WebEditorFieldAction(
    val id: String,
    val label: String,
    val iconPath: String,
    val iconViewBox: String = "0 0 24 24",
    val active: Boolean? = null,
    val richText: WebEditorFieldActionAppearance? = null,
)

/** Alternate appearance; the web editor supplies the current field mode. */
data class WebEditorFieldActionAppearance(
    val label: String,
    val iconPath: String,
    val iconViewBox: String = "0 0 24 24",
)

data class WebEditorStatus(
    val revision: Int,
    val hasChanges: Boolean,
    val composing: Boolean = false,
    val hasSelection: Boolean = false,
    val activeField: Int? = null,
    val sourceMode: Boolean = false,
    val activeFormats: Set<WebEditorAction> = emptySet(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
)

data class WebEditorSnapshot(
    val sessionId: String,
    val generation: Int,
    val revision: Int,
    val fields: List<String>,
    val hasChanges: Boolean,
)

/** A conservative bookmark: edits to the destination field invalidate delayed commands. */
@Parcelize
data class WebEditorTarget(
    val sessionId: String,
    val generation: Int,
    val field: Int,
    val revision: Int,
    val bookmark: String,
) : Parcelable

/** The active input can differ from an attachment's quick-action destination. */
@Parcelize
data class WebEditorFocusState(
    val target: WebEditorTarget?,
    val keyboardVisible: Boolean,
) : Parcelable

enum class WebEditorAction {
    BOLD,
    ITALIC,
    UNDERLINE,
    SUPERSCRIPT,
    SUBSCRIPT,
    TEXT_COLOR,
    HIGHLIGHT_COLOR,
    REMOVE_FORMAT,
    REMOVE_FORMAT_CHOICES,
    BULLETED_LIST,
    NUMBERED_LIST,
    ALIGN_LEFT,
    ALIGN_CENTER,
    ALIGN_RIGHT,
    ALIGN_JUSTIFY,
    INDENT,
    OUTDENT,
    MATHJAX_INLINE,
    MATHJAX_BLOCK,
    MATHJAX_CHEMISTRY,
    LATEX,
    LATEX_EQUATION,
    LATEX_MATH,
    CLOZE_NEW,
    CLOZE_SAME,
    UNDO,
    REDO,
    INSERT_TEXT,
    INSERT_HTML,
    WRAP_HTML,
    SOURCE_MODE,
}

data class WebEditorDraft(
    val document: WebEditorDocument,
    val baseline: List<String>,
    val hostStateJson: String,
)
