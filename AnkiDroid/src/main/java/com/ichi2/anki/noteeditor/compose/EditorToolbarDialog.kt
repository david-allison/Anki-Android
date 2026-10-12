// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
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
    val allItems = toolbar.orderedItems(templates)
    val items = allItems.filter { it.appliesTo(isCloze) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.compose_editor_toolbar)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(field.name, style = MaterialTheme.typography.titleMedium)
                Text(noteTypeName, style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.compose_editor_toolbar_scope), Modifier.padding(vertical = 12.dp))
                items.forEachIndexed { index, item ->
                    key(item.key) {
                        val label =
                            when (item) {
                                is EditorToolbarItem.Builtin -> stringResource(item.button.label)
                                is EditorToolbarItem.Custom -> item.button.label
                            }
                        val checked =
                            when (item) {
                                is EditorToolbarItem.Builtin -> item.key !in toolbar.hiddenButtons
                                is EditorToolbarItem.Custom -> toolbar.customButtons.any { it.id == item.button.id }
                            }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Row(
                                Modifier.weight(1f).heightIn(min = 48.dp).toggleable(
                                    value = checked,
                                    role = Role.Checkbox,
                                    onValueChange = { shown ->
                                        // Establish the order before toggling, so hidden items keep their slots.
                                        val ordered = toolbar.copy(buttonOrder = allItems.map { it.key })
                                        toolbar =
                                            when (item) {
                                                is EditorToolbarItem.Builtin ->
                                                    ordered.copy(
                                                        hiddenButtons =
                                                            if (shown) {
                                                                toolbar.hiddenButtons - item.key
                                                            } else {
                                                                toolbar.hiddenButtons +
                                                                    item.key
                                                            },
                                                    )
                                                is EditorToolbarItem.Custom ->
                                                    ordered.copy(
                                                        customButtons =
                                                            if (shown) {
                                                                toolbar.customButtons + item.button
                                                            } else {
                                                                toolbar.customButtons.filterNot { it.id == item.button.id }
                                                            },
                                                    )
                                            }
                                    },
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked, onCheckedChange = null)
                                Box(Modifier.padding(start = 12.dp).size(20.dp)) {
                                    if (item is EditorToolbarItem.Builtin) {
                                        Icon(painterResource(item.button.icon), contentDescription = null)
                                    }
                                }
                                Text(label, Modifier.padding(start = 12.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(
                                onClick = { toolbar = toolbar.moveButton(item.key, -1, templates, isCloze) },
                                enabled = index > 0,
                            ) {
                                Icon(
                                    painterResource(R.drawable.outline_arrow_upward_alt_24),
                                    stringResource(R.string.compose_editor_move_button_up, label),
                                )
                            }
                            IconButton(
                                onClick = { toolbar = toolbar.moveButton(item.key, 1, templates, isCloze) },
                                enabled = index < items.lastIndex,
                            ) {
                                Icon(
                                    painterResource(R.drawable.outline_arrow_downward_alt_24),
                                    stringResource(R.string.compose_editor_move_button_down, label),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(toolbar) }) { Text(stringResource(CommonString.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CommonString.dialog_cancel)) } },
    )
}
