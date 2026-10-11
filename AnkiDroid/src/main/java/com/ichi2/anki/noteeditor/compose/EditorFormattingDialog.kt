// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.os.Parcelable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.noteeditor.web.WebEditorAction
import com.ichi2.anki.noteeditor.web.WebEditorTarget
import kotlinx.parcelize.Parcelize

/** The destination is captured before a native dialog takes focus. */
@Parcelize
internal data class EditorFormattingRequest(
    val action: WebEditorAction,
    val target: WebEditorTarget,
    val text: String,
    val editingEquation: Boolean = false,
) : Parcelable

private val equationKinds =
    listOf(
        WebEditorAction.MATHJAX_INLINE to R.string.compose_editor_math_inline,
        WebEditorAction.MATHJAX_BLOCK to R.string.compose_editor_math_block,
        WebEditorAction.MATHJAX_CHEMISTRY to R.string.compose_editor_math_chemistry,
        WebEditorAction.LATEX to R.string.compose_editor_latex,
        WebEditorAction.LATEX_EQUATION to R.string.compose_editor_latex_equation,
        WebEditorAction.LATEX_MATH to R.string.compose_editor_latex_math,
    )

private val alignments =
    listOf(
        WebEditorAction.ALIGN_LEFT to R.string.compose_editor_align_left,
        WebEditorAction.ALIGN_CENTER to R.string.compose_editor_align_center,
        WebEditorAction.ALIGN_RIGHT to R.string.compose_editor_align_right,
        WebEditorAction.ALIGN_JUSTIFY to R.string.compose_editor_align_justify,
        WebEditorAction.INDENT to R.string.compose_editor_indent,
        WebEditorAction.OUTDENT to R.string.compose_editor_outdent,
    )

private val palette = listOf("#000000", "#ffffff", "#d32f2f", "#f57c00", "#fbc02d", "#388e3c", "#1976d2", "#7b1fa2")
internal val editorColourPattern = Regex("#[0-9a-fA-F]{6}")

@Composable
internal fun EditorFormattingDialog(
    request: EditorFormattingRequest,
    recentColours: List<String>,
    onDismiss: () -> Unit,
    onApply: (WebEditorAction, String) -> Unit,
) {
    var text by rememberSaveable(request) { mutableStateOf(request.text) }
    var action by rememberSaveable(request) { mutableStateOf(request.action) }
    val colour = request.action in setOf(WebEditorAction.TEXT_COLOR, WebEditorAction.HIGHLIGHT_COLOR)
    val alignment = request.action == WebEditorAction.ALIGN_LEFT
    val valid = if (colour) editorColourPattern.matches(text.trim()) else text.isNotBlank() || request.editingEquation
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    when {
                        alignment -> R.string.compose_editor_alignment
                        request.action == WebEditorAction.TEXT_COLOR -> R.string.compose_editor_text_color
                        colour -> R.string.compose_editor_highlight_color
                        else -> R.string.compose_editor_equations
                    },
                ),
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when {
                    alignment ->
                        alignments.forEach { (command, label) ->
                            TextButton(onClick = { onApply(command, "") }) { Text(stringResource(label)) }
                        }
                    colour -> {
                        (recentColours + palette).filter(editorColourPattern::matches).distinct().take(12).chunked(4).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEach { hex ->
                                    Surface(
                                        onClick = { text = hex },
                                        modifier = Modifier.size(48.dp).semantics { contentDescription = hex },
                                        shape = CircleShape,
                                        color = Color(android.graphics.Color.parseColor(hex)),
                                        border = BorderStroke(if (hex == text) 3.dp else 1.dp, MaterialTheme.colorScheme.outline),
                                    ) {}
                                }
                            }
                        }
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            label = { Text(stringResource(R.string.compose_editor_colour_hex)) },
                            isError = !valid,
                            singleLine = true,
                        )
                    }
                    else -> {
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            TextButton(onClick = { expanded = true }) {
                                Text(stringResource(equationKinds.first { it.first == action }.second))
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                containerColor =
                                    MaterialTheme.colorScheme.surfaceContainer.compositeOver(
                                        MaterialTheme.colorScheme.surface,
                                    ),
                            ) {
                                equationKinds.forEach { (command, label) ->
                                    DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
                                        action = command
                                        expanded = false
                                    })
                                }
                            }
                        }
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            label = { Text(stringResource(R.string.compose_editor_formula)) },
                            minLines = 3,
                            maxLines = 6,
                        )
                        if (action in setOf(WebEditorAction.LATEX, WebEditorAction.LATEX_EQUATION, WebEditorAction.LATEX_MATH)) {
                            Text(stringResource(R.string.compose_editor_latex_help), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!alignment) {
                TextButton(onClick = { onApply(action, if (colour) text.trim() else text) }, enabled = valid) {
                    Text(stringResource(CommonString.dialog_ok))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CommonString.dialog_cancel)) } },
    )
}
