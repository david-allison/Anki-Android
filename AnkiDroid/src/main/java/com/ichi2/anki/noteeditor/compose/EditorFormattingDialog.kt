// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.os.Parcelable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.noteeditor.web.WebEditorAction
import com.ichi2.anki.noteeditor.web.WebEditorTarget
import com.ichi2.anki.ui.windows.reviewer.whiteboard.showColorPickerDialog
import com.ichi2.anki.utils.openUrl
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
    val equation = !removing && !alignment
    val valid =
        when {
            removing -> removedFormats.isNotEmpty()
            else -> text.isNotBlank() || request.editingEquation
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon =
            if (equation) {
                { Icon(painterResource(R.drawable.ic_add_equation_black_24dp), contentDescription = null) }
            } else {
                null
            },
        title = {
            val title =
                stringResource(
                    when {
                        removing -> R.string.compose_editor_remove_format
                        alignment -> R.string.compose_editor_alignment
                        request.editingEquation -> R.string.compose_editor_edit_equation
                        else -> R.string.compose_editor_add_equation
                    },
                )
            if (equation) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, modifier = Modifier.weight(1f))
                    val context = LocalContext.current
                    EditorActionButton(
                        icon = R.drawable.help_outline,
                        label = R.string.compose_editor_math_help,
                        enabled = true,
                        onClick = { context.openUrl(R.string.link_manual_mathjax) },
                    )
                }
            } else {
                Text(title)
            }
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
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            equationKinds.forEachIndexed { index, (command, label) ->
                                SegmentedButton(
                                    selected = action == command,
                                    onClick = { action = command },
                                    shape = SegmentedButtonDefaults.itemShape(index, equationKinds.size),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                    icon = {},
                                ) {
                                    Text(stringResource(label))
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
