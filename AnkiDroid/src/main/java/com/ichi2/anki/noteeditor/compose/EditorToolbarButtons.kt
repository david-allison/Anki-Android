// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.noteeditor.web.WebEditorAction

/** Names are persisted; keep them stable when changing labels or toolbar order. */
internal enum class EditorToolbarButton(
    val icon: Int,
    val label: Int,
    val action: WebEditorAction? = null,
    val toggle: Boolean = false,
    val richOnly: Boolean = false,
    val clozeOnly: Boolean = false,
) {
    BOLD(R.drawable.ic_format_bold_black_24dp, R.string.compose_editor_bold, WebEditorAction.BOLD, toggle = true),
    ITALIC(R.drawable.ic_format_italic_black_24dp, R.string.compose_editor_italic, WebEditorAction.ITALIC, toggle = true),
    UNDERLINE(R.drawable.ic_format_underlined_black_24dp, R.string.compose_editor_underline, WebEditorAction.UNDERLINE, toggle = true),
    CLOZE_NEW(R.drawable.ic_cloze_new_card, R.string.compose_editor_cloze_new, WebEditorAction.CLOZE_NEW, clozeOnly = true),
    CLOZE_SAME(R.drawable.ic_cloze_same_card, R.string.compose_editor_cloze_same, WebEditorAction.CLOZE_SAME, clozeOnly = true),
    UNDO(R.drawable.ic_undo_2, CommonString.undo, WebEditorAction.UNDO),
    REDO(R.drawable.ic_redo_2, CommonString.redo, WebEditorAction.REDO),
    SOURCE_MODE(R.drawable.ic_code, R.string.compose_editor_source, WebEditorAction.SOURCE_MODE, toggle = true),
    MEDIA(R.drawable.ic_attachment, R.string.compose_editor_media),
    SUPERSCRIPT(R.drawable.ic_editor_format_superscript, R.string.compose_editor_superscript, WebEditorAction.SUPERSCRIPT, toggle = true),
    SUBSCRIPT(R.drawable.ic_editor_format_subscript, R.string.compose_editor_subscript, WebEditorAction.SUBSCRIPT, toggle = true),
    TEXT_COLOR(R.drawable.ic_editor_format_color_text, R.string.compose_editor_text_color, WebEditorAction.TEXT_COLOR),
    HIGHLIGHT_COLOR(R.drawable.ic_editor_format_color_highlight, R.string.compose_editor_highlight_color, WebEditorAction.HIGHLIGHT_COLOR),
    REMOVE_FORMAT(R.drawable.ic_editor_eraser, R.string.compose_editor_remove_format, WebEditorAction.REMOVE_FORMAT, richOnly = true),
    BULLETED_LIST(
        R.drawable.ic_editor_format_list_bulleted,
        R.string.compose_editor_bulleted_list,
        WebEditorAction.BULLETED_LIST,
        toggle = true,
        richOnly = true,
    ),
    NUMBERED_LIST(
        R.drawable.ic_editor_format_list_numbered,
        R.string.compose_editor_numbered_list,
        WebEditorAction.NUMBERED_LIST,
        toggle = true,
        richOnly = true,
    ),
    ALIGNMENT(R.drawable.ic_editor_format_align_left, R.string.compose_editor_alignment, WebEditorAction.ALIGN_LEFT, richOnly = true),
    EQUATIONS(R.drawable.ic_add_equation_black_24dp, R.string.compose_editor_equations, WebEditorAction.MATHJAX_INLINE),
    RECORD_AUDIO(R.drawable.ic_action_mic, CommonString.multimedia_editor_popup_audio),
}

@Composable
internal fun EditorFormattingButtons(
    toolbar: EditorFieldToolbar,
    state: EditorToolbarState,
    enabled: Boolean,
    isCloze: Boolean,
    textColor: String = "#000000",
    highlightColor: String = "#ffff00",
    onAction: (WebEditorAction) -> Unit,
    onCustomAction: (EditorCustomToolbarButton) -> Unit,
    onMedia: () -> Unit,
    onRecordAudio: () -> Unit,
) {
    val formatEnabled = enabled && state.hasSelection && !state.composing
    toolbar.orderedItems().filter { it.appliesTo(isCloze) }.forEach { item ->
        if (item is EditorToolbarItem.Custom) {
            EditorTooltip(item.button.label) {
                TextButton(onClick = { onCustomAction(item.button) }, enabled = formatEnabled) {
                    Text(item.button.label, Modifier.widthIn(max = 60.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            return@forEach
        }
        val button = (item as EditorToolbarItem.Builtin).button
        if (button.name in toolbar.hiddenButtons) return@forEach
        val actionEnabled =
            if (button == EditorToolbarButton.SOURCE_MODE) {
                enabled && state.hasSelection
            } else {
                formatEnabled && (!state.sourceMode || !button.richOnly)
            }
        val click = {
            when (button) {
                EditorToolbarButton.MEDIA -> onMedia()
                EditorToolbarButton.RECORD_AUDIO -> onRecordAudio()
                else -> onAction(requireNotNull(button.action))
            }
        }
        if (button == EditorToolbarButton.SOURCE_MODE) {
            EditorModeButton(state.sourceMode, actionEnabled, click)
        } else if (button.toggle) {
            val selected = button.action in state.activeFormats
            EditorToggleButton(button.icon, button.label, actionEnabled, selected, click)
        } else if (button == EditorToolbarButton.TEXT_COLOR || button == EditorToolbarButton.HIGHLIGHT_COLOR) {
            EditorColorButton(button, if (button == EditorToolbarButton.TEXT_COLOR) textColor else highlightColor, actionEnabled, click)
        } else {
            EditorActionButton(button.icon, button.label, actionEnabled, click)
        }
    }
}

@Composable
private fun EditorColorButton(
    button: EditorToolbarButton,
    color: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(button.label)
    val shape = RoundedCornerShape(1.dp)
    EditorTooltip(label) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.semantics { stateDescription = color }) {
            Box(Modifier.size(24.dp)) {
                Icon(painterResource(button.icon), label, Modifier.fillMaxSize())
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color(android.graphics.Color.parseColor(color)).copy(alpha = if (enabled) 1f else 0.38f), shape)
                        .border(0.5.dp, MaterialTheme.colorScheme.outline, shape),
                )
            }
        }
    }
}
