// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

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
    val decks: List<EditorChoice>,
    val noteTypes: List<EditorChoice>,
    /** Type changes map the existing web baseline; starting the next Add note establishes a new one. */
    val resetFieldBaseline: Boolean = true,
    val isSaving: Boolean = false,
    val hasMetadataChanges: Boolean = false,
)

data class EditorChoice(
    val id: Long,
    val name: String,
)

data class EditorFieldState(
    val name: String,
    val html: String,
    val font: String,
    val fontSize: Int,
    val rtl: Boolean,
    val languageTag: String?,
    val sticky: Boolean,
    val collapsed: Boolean,
    val sourceMode: Boolean,
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
    ) : EditorSaveResult
}
