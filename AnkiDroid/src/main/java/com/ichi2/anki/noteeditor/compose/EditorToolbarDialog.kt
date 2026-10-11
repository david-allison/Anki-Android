// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
    templates: List<EditorCustomToolbarButton>,
    onDismiss: () -> Unit,
    onSave: (EditorFieldToolbar) -> Unit,
) {
    var toolbar by rememberSaveable(target) { mutableStateOf(field.toolbar) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.compose_editor_toolbar)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(field.name, style = MaterialTheme.typography.titleMedium)
                Text(noteTypeName, style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.compose_editor_toolbar_scope), Modifier.padding(vertical = 12.dp))
                Text(stringResource(R.string.compose_editor_custom_buttons), style = MaterialTheme.typography.titleSmall)
                templates.forEach { custom ->
                    val checked = toolbar.customButtons.any { it.id == custom.id }
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                            value = checked,
                            role = Role.Checkbox,
                            onValueChange = { shown ->
                                toolbar =
                                    toolbar.copy(
                                        customButtons =
                                            if (shown) {
                                                toolbar.customButtons + custom
                                            } else {
                                                toolbar.customButtons.filterNot {
                                                    it.id ==
                                                        custom.id
                                                }
                                            },
                                    )
                            },
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked, onCheckedChange = null)
                        Text(custom.label, Modifier.padding(start = 12.dp))
                    }
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
