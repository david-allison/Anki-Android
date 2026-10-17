// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ichi2.anki.CommonString
import com.ichi2.anki.R

@Composable
internal fun EditorCustomButtonsDialog(
    templates: List<EditorCustomToolbarButton>,
    onDismiss: () -> Unit,
    onSave: (List<EditorCustomToolbarButton>) -> Unit,
) {
    var buttons by rememberSaveable { mutableStateOf(templates) }
    var editing by rememberSaveable { mutableStateOf<EditorCustomToolbarButton?>(null) }
    val button = editing
    if (button != null) {
        CustomToolbarButtonDialog(
            button = button,
            onDismiss = { editing = null },
            onSave = { updated ->
                buttons =
                    if (buttons.any { it.id == updated.id }) {
                        buttons.map { if (it.id == updated.id) updated else it }
                    } else {
                        buttons + updated
                    }
                editing = null
            },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_widgets), null, tint = editorControlColor()) },
        title = { Text(stringResource(R.string.compose_editor_custom_buttons)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.compose_editor_custom_buttons_scope), Modifier.padding(bottom = 12.dp))
                buttons.forEach { custom ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier.weight(1f).heightIn(min = 48.dp).clickable { editing = custom },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(painterResource(R.drawable.ic_mode_edit_white), null, Modifier.size(20.dp), tint = editorControlColor())
                            Spacer(Modifier.width(12.dp))
                            Text(custom.label)
                        }
                        IconButton(onClick = { buttons = buttons.filterNot { it.id == custom.id } }, colors = editorIconButtonColors()) {
                            Icon(
                                painterResource(R.drawable.ic_delete),
                                stringResource(R.string.compose_editor_remove_custom_button, custom.label),
                            )
                        }
                    }
                }
                TextButton(onClick = { editing = EditorCustomToolbarButton(label = "", prefix = "", suffix = "") }) {
                    Icon(painterResource(R.drawable.ic_add_toolbar_icon), null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(CommonString.add_toolbar_item))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(buttons) }) { Text(stringResource(CommonString.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CommonString.dialog_cancel)) } },
    )
}

@Composable
private fun CustomToolbarButtonDialog(
    button: EditorCustomToolbarButton,
    onDismiss: () -> Unit,
    onSave: (EditorCustomToolbarButton) -> Unit,
) {
    var label by rememberSaveable(button.id) { mutableStateOf(button.label) }
    var prefix by rememberSaveable(button.id) { mutableStateOf(button.prefix) }
    var suffix by rememberSaveable(button.id) { mutableStateOf(button.suffix) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.compose_editor_custom_button)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    label,
                    { label = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(CommonString.note_editor_toolbar_icon)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    prefix,
                    { prefix = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(CommonString.before_text)) },
                )
                OutlinedTextField(
                    suffix,
                    { suffix = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(CommonString.after_text)) },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(button.copy(label = label.trim(), prefix = prefix, suffix = suffix)) },
                enabled = label.isNotBlank() && (prefix.isNotEmpty() || suffix.isNotEmpty()),
            ) { Text(stringResource(CommonString.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CommonString.dialog_cancel)) } },
    )
}
