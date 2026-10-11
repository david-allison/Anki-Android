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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ichi2.anki.CommonString
import com.ichi2.anki.R

@Composable
internal fun EditorToolbarDialog(
    target: EditorFieldTarget,
    field: EditorFieldState,
    noteTypeName: String,
    isCloze: Boolean,
    onDismiss: () -> Unit,
    onSave: (EditorFieldToolbar) -> Unit,
) {
    var toolbar by rememberSaveable(target) { mutableStateOf(field.toolbar) }
    var editing by rememberSaveable(target) { mutableStateOf<EditorCustomToolbarButton?>(null) }
    val button = editing
    if (button != null) {
        CustomToolbarButtonDialog(
            button = button,
            onDismiss = { editing = null },
            onSave = { updated ->
                val existing = toolbar.customButtons.any { it.id == updated.id }
                toolbar =
                    toolbar.copy(
                        customButtons =
                            if (existing) {
                                toolbar.customButtons.map { if (it.id == updated.id) updated else it }
                            } else {
                                toolbar.customButtons + updated
                            },
                    )
                editing = null
            },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.compose_editor_toolbar)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(field.name, style = MaterialTheme.typography.titleMedium)
                Text(noteTypeName, style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.compose_editor_toolbar_scope), Modifier.padding(vertical = 12.dp))
                Text(stringResource(R.string.compose_editor_custom_buttons), style = MaterialTheme.typography.titleSmall)
                toolbar.customButtons.forEach { custom ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier.weight(1f).heightIn(min = 48.dp).clickable { editing = custom },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(painterResource(R.drawable.ic_mode_edit_white), null, Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(custom.label)
                        }
                        IconButton(onClick = {
                            toolbar =
                                toolbar.copy(customButtons = toolbar.customButtons.filterNot { it.id == custom.id })
                        }) {
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
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text(stringResource(R.string.compose_editor_standard_buttons), style = MaterialTheme.typography.titleSmall)
                EditorToolbarButton.entries.filter { !it.clozeOnly || isCloze }.forEach { builtin ->
                    val checked = builtin.name !in toolbar.hiddenButtons
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                            value = checked,
                            role = Role.Checkbox,
                            onValueChange = { shown ->
                                toolbar =
                                    toolbar.copy(
                                        hiddenButtons =
                                            if (shown) {
                                                toolbar.hiddenButtons - builtin.name
                                            } else {
                                                toolbar.hiddenButtons +
                                                    builtin.name
                                            },
                                    )
                            },
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked, onCheckedChange = null)
                        Icon(painterResource(builtin.icon), null, Modifier.padding(horizontal = 12.dp).size(20.dp))
                        Text(stringResource(builtin.label))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(toolbar) }) { Text(stringResource(CommonString.save)) } },
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
                    label = { Text(stringResource(R.string.note_editor_toolbar_icon)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    prefix,
                    { prefix = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.before_text)) },
                )
                OutlinedTextField(
                    suffix,
                    { suffix = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.after_text)) },
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
