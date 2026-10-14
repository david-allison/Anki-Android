// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.os.Parcelable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.noteeditor.web.WebEditorAction
import com.ichi2.anki.noteeditor.web.WebEditorTarget
import com.ichi2.anki.ui.windows.reviewer.whiteboard.showColorPickerDialog
import kotlinx.parcelize.Parcelize
import org.json.JSONArray

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

internal val editorRemovableFormats =
    listOf(
        WebEditorAction.BOLD to R.string.compose_editor_bold,
        WebEditorAction.ITALIC to R.string.compose_editor_italic,
        WebEditorAction.UNDERLINE to R.string.compose_editor_underline,
        WebEditorAction.SUPERSCRIPT to R.string.compose_editor_superscript,
        WebEditorAction.SUBSCRIPT to R.string.compose_editor_subscript,
        WebEditorAction.TEXT_COLOR to R.string.compose_editor_text_color,
        WebEditorAction.HIGHLIGHT_COLOR to R.string.compose_editor_highlight_color,
    )

internal val editorColourPattern = Regex("#[0-9a-fA-F]{6}")

@Composable
internal fun EditorFormattingDialog(
    request: EditorFormattingRequest,
    onDismiss: () -> Unit,
    onApply: (WebEditorAction, String) -> Unit,
) {
    if (request.action in setOf(WebEditorAction.TEXT_COLOR, WebEditorAction.HIGHLIGHT_COLOR)) {
        val context = LocalContext.current
        val dismiss by rememberUpdatedState(onDismiss)
        val apply by rememberUpdatedState(onApply)
        DisposableEffect(context, request) {
            var disposed = false
            val dialog =
                context.showColorPickerDialog(
                    initialColor = android.graphics.Color.parseColor(request.text),
                    showAlphaSlider = false,
                    onDismiss = { if (!disposed) dismiss() },
                ) { color -> apply(request.action, "#%06x".format(color and 0xffffff)) }
            onDispose {
                disposed = true
                dialog.dismiss()
            }
        }
        return
    }
    var text by rememberSaveable(request) { mutableStateOf(request.text) }
    var action by rememberSaveable(request) { mutableStateOf(request.action) }
    val removing = request.action == WebEditorAction.REMOVE_FORMAT_CHOICES
    var removedFormats by rememberSaveable(request) {
        mutableStateOf(
            runCatching {
                val choices = JSONArray(request.text)
                editorRemovableFormats.map { it.first.name }.filter { name ->
                    (0 until choices.length()).any { choices.optString(it) == name }
                }
            }.getOrDefault(editorRemovableFormats.map { it.first.name }),
        )
    }
    val alignment = request.action == WebEditorAction.ALIGN_LEFT
    val valid =
        when {
            removing -> removedFormats.isNotEmpty()
            else -> text.isNotBlank() || request.editingEquation
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    when {
                        removing -> R.string.compose_editor_remove_format
                        alignment -> R.string.compose_editor_alignment
                        else -> R.string.compose_editor_equations
                    },
                ),
            )
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(if (removing) 0.dp else 16.dp),
            ) {
                when {
                    removing ->
                        editorRemovableFormats.forEach { (format, label) ->
                            val checked = format.name in removedFormats
                            Row(
                                modifier =
                                    Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                                        value = checked,
                                        role = Role.Checkbox,
                                        onValueChange = { selected ->
                                            removedFormats = if (selected) removedFormats + format.name else removedFormats - format.name
                                        },
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = checked, onCheckedChange = null)
                                Text(stringResource(label), Modifier.padding(start = 12.dp))
                            }
                        }
                    alignment ->
                        alignments.forEach { (command, label) ->
                            TextButton(onClick = { onApply(command, "") }) { Text(stringResource(label)) }
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
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.compose_editor_formula)) },
                            minLines = 2,
                            maxLines = 4,
                        )
                        EquationPreview(text, action)
                        if (action in setOf(WebEditorAction.LATEX, WebEditorAction.LATEX_EQUATION, WebEditorAction.LATEX_MATH)) {
                            Text(stringResource(R.string.compose_editor_latex_help), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!alignment) {
                TextButton(onClick = {
                    onApply(
                        action,
                        when {
                            removing -> JSONArray(removedFormats).toString()
                            else -> text
                        },
                    )
                }, enabled = valid) {
                    Text(stringResource(CommonString.dialog_ok))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CommonString.dialog_cancel)) } },
    )
}
