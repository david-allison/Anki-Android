// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ichi2.anki.CommonString
import com.ichi2.anki.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorQuickActionsDialog(
    target: EditorFieldTarget,
    field: EditorFieldState,
    noteTypeName: String,
    onDismiss: () -> Unit,
    onSave: (EditorFieldToolbar) -> Unit,
) {
    var toolbar by rememberSaveable(target) { mutableStateOf(field.toolbar) }
    var order by rememberSaveable(target) {
        mutableStateOf(
            (field.toolbar.quickActions + EditorQuickAction.entries.map { it.id }).distinct().filter {
                EditorQuickAction.fromId(it) !=
                    null
            },
        )
    }
    val items = order.sortedBy { it !in toolbar.quickActions }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.compose_editor_quick_actions)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(painterResource(R.drawable.ic_clear_white), stringResource(CommonString.dialog_cancel))
                        }
                    },
                    actions = {
                        EditorSaveButton(onClick = { onSave(toolbar) }, modifier = Modifier.padding(end = 8.dp))
                    },
                )
            },
        ) { insets ->
            EditorToolbarList(
                items = items,
                canMove = { from, to -> (from in toolbar.quickActions) == (to in toolbar.quickActions) },
                onMove = { from, to ->
                    order = order.toMutableList().apply { add(indexOf(to), removeAt(indexOf(from))) }
                    toolbar = toolbar.copy(quickActions = order.filter { it in toolbar.quickActions })
                },
                modifier = Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp),
                header = {
                    Column {
                        Text(field.name, style = MaterialTheme.typography.titleMedium)
                        Text(noteTypeName, style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.compose_editor_quick_actions_scope), Modifier.padding(top = 12.dp))
                        Text(stringResource(R.string.compose_editor_quick_actions_insertion), Modifier.padding(vertical = 12.dp))
                    }
                },
            ) { key, index, dragging, startDrag, move ->
                val item = requireNotNull(EditorQuickAction.fromId(key))
                val checked = key in toolbar.quickActions
                EditorToolbarRow(
                    item.icon,
                    stringResource(item.label),
                    checked,
                    dragging,
                    onCheckedChange = { shown ->
                        val (selected, available) = items.filterNot { it == key }.partition { it in toolbar.quickActions }
                        order = selected + key + available
                        val enabled = if (shown) toolbar.quickActions + key else toolbar.quickActions - key
                        toolbar = toolbar.copy(quickActions = order.filter { it in enabled })
                    },
                    startDrag = startDrag,
                    moveUp = if (index > 0 && (items[index - 1] in toolbar.quickActions) == checked) ({ move(-1) }) else null,
                    moveDown =
                        if (index < items.lastIndex &&
                            (items[index + 1] in toolbar.quickActions) == checked
                        ) {
                            ({ move(1) })
                        } else {
                            null
                        },
                )
            }
        }
    }
}
