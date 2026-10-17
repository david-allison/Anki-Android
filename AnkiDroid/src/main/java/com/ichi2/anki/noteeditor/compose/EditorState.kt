// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.os.Parcelable
import com.ichi2.anki.noteeditor.web.WebEditorSettings
import kotlinx.parcelize.Parcelize

/** Values needed by the host and the field editor, without retaining a mutable libanki note. */
data class EditorState(
    val sessionId: String,
    val generation: Int,
    val isAdding: Boolean,
    val noteId: Long,
    val cardId: Long?,
    val deckId: Long,
    val deckName: String,
    val noteTypeId: Long,
    val noteTypeName: String,
    val notetypeJson: String,
    val mediaDirectory: String,
    val isCloze: Boolean,
    val fields: List<EditorFieldState>,
    val tags: List<String>,
    val customButtonTemplates: List<EditorCustomToolbarButton> = emptyList(),
    val toolbarPresets: List<EditorToolbarPreset> = listOf(EditorToolbarPreset(id = EditorToolbarPreset.DEFAULT_ID, name = "")),
    val settings: WebEditorSettings = WebEditorSettings(),
    val toolbarDisplay: EditorToolbarDisplay = EditorToolbarDisplay.SCROLL,
    val toolbarTextColor: String = "#000000",
    val toolbarHighlightColor: String = "#ffff00",
    val baselineFieldMap: List<Int>? = null,
    val removedFields: List<EditorRemovedField> = emptyList(),
    /** Type changes map the existing web baseline; starting the next Add note establishes a new one. */
    val resetFieldBaseline: Boolean = true,
    val isSaving: Boolean = false,
    val hasMetadataChanges: Boolean = false,
)

data class EditorChoice(
    val id: Long,
    val name: String,
)

/** A settings dialog keeps its field even when focus moves to native controls. */
@Parcelize
data class EditorFieldTarget(
    val sessionId: String,
    val generation: Int,
    val field: Int,
) : Parcelable {
    fun matches(state: EditorState?): Boolean =
        state != null && state.sessionId == sessionId && state.generation == generation && field in state.fields.indices
}

data class EditorFieldState(
    val name: String,
    val html: String,
    val font: String,
    val fontSize: Int,
    val rtl: Boolean,
    val languageTag: String?,
    val sticky: Boolean,
    /** Note-type default; temporary expansion lives in the WebView and its draft. */
    val collapsed: Boolean,
    /** Note-type default; the active editing mode lives in the WebView and its draft. */
    val sourceMode: Boolean,
    val toolbar: EditorFieldToolbar = EditorFieldToolbar(),
    val toolbarPresetId: String = EditorToolbarPreset.DEFAULT_ID,
)

sealed interface EditorSaveResult {
    /** Editing is complete: the host closes the editor. */
    data object Saved : EditorSaveResult

    /** The next Add document is already in [EditorViewModel.state], including retained fields. */
    data class Added(
        val cardCount: Int,
    ) : EditorSaveResult

    /** Leave the document open, with all its contents available for correction/retry. */
    data class Invalid(
        val message: String?,
        val canSaveAnyway: Boolean = false,
        val fieldIndex: Int? = null,
    ) : EditorSaveResult
}

/** The device layout can hide every toolbar without replacing any field's saved choice. */
internal fun EditorState.showsToolbar(activeField: Int?): Boolean =
    toolbarDisplay != EditorToolbarDisplay.HIDDEN &&
        fields.getOrNull(activeField ?: 0)?.toolbar?.showToolbar != false

/** Automatic focus must not expand fields the user has chosen to collapse by default. */
internal val EditorState.initialFocusField: Int?
    get() = fields.indexOfFirst { !it.collapsed }.takeIf { it >= 0 }
