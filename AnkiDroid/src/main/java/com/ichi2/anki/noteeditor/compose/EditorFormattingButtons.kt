// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import androidx.compose.runtime.Composable
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.noteeditor.web.WebEditorAction

private data class FormattingButton(
    val icon: Int,
    val label: Int,
    val action: WebEditorAction,
    val richOnly: Boolean = false,
    val toggle: Boolean = false,
)

private val formattingButtons =
    listOf(
        FormattingButton(
            R.drawable.ic_editor_format_superscript,
            R.string.compose_editor_superscript,
            WebEditorAction.SUPERSCRIPT,
            toggle = true,
        ),
        FormattingButton(
            R.drawable.ic_editor_format_subscript,
            R.string.compose_editor_subscript,
            WebEditorAction.SUBSCRIPT,
            toggle = true,
        ),
        FormattingButton(R.drawable.ic_editor_format_color_text, R.string.compose_editor_text_color, WebEditorAction.TEXT_COLOR),
        FormattingButton(
            R.drawable.ic_editor_format_color_highlight,
            R.string.compose_editor_highlight_color,
            WebEditorAction.HIGHLIGHT_COLOR,
        ),
        FormattingButton(
            R.drawable.ic_editor_eraser,
            R.string.compose_editor_remove_format,
            WebEditorAction.REMOVE_FORMAT,
            richOnly = true,
        ),
        FormattingButton(
            R.drawable.ic_editor_format_list_bulleted,
            R.string.compose_editor_bulleted_list,
            WebEditorAction.BULLETED_LIST,
            richOnly = true,
            toggle = true,
        ),
        FormattingButton(
            R.drawable.ic_editor_format_list_numbered,
            R.string.compose_editor_numbered_list,
            WebEditorAction.NUMBERED_LIST,
            richOnly = true,
            toggle = true,
        ),
        FormattingButton(
            R.drawable.ic_editor_format_align_left,
            R.string.compose_editor_alignment,
            WebEditorAction.ALIGN_LEFT,
            richOnly = true,
        ),
        FormattingButton(R.drawable.ic_add_equation_black_24dp, R.string.compose_editor_equations, WebEditorAction.MATHJAX_INLINE),
    )

@Composable
internal fun ExtraFormattingButtons(
    enabled: Boolean,
    sourceMode: Boolean,
    activeFormats: Set<WebEditorAction>,
    onAction: (WebEditorAction) -> Unit,
    onRecordAudio: () -> Unit,
) {
    formattingButtons.forEach { button ->
        val actionEnabled = enabled && (!sourceMode || !button.richOnly)
        if (button.toggle) {
            EditorToggleButton(button.icon, button.label, actionEnabled, button.action in activeFormats) { onAction(button.action) }
        } else {
            EditorActionButton(button.icon, button.label, actionEnabled) { onAction(button.action) }
        }
    }
    EditorActionButton(R.drawable.ic_action_mic, CommonString.multimedia_editor_popup_audio, enabled, onRecordAudio)
}
