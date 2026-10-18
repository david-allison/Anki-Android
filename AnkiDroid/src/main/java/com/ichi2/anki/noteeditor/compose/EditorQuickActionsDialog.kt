// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ichi2.anki.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorQuickActionsDialog(
    target: EditorFieldTarget,
    field: EditorFieldState,
    onDismiss: () -> Unit,
    onChange: (EditorFieldToolbar) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.compose_editor_quick_actions)) },
                )
            },
        ) { insets ->
            key(target) {
                EditorQuickActionsContent(field.toolbar, onChange, Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
internal fun EditorQuickActionsContent(
    toolbar: EditorFieldToolbar,
    onChange: (EditorFieldToolbar) -> Unit,
    modifier: Modifier = Modifier,
    onDraggingChange: (Boolean) -> Unit = {},
) {
    // Keep touch feedback local while persistence catches up. A drag writes once, on release.
    var quickActions by rememberSaveable { mutableStateOf(toolbar.quickActions) }
    var dragging by remember { mutableStateOf(false) }

    fun change(actions: List<String>) {
        quickActions = actions
        if (!dragging) onChange(toolbar.copy(quickActions = actions))
    }
    var order by rememberSaveable {
        mutableStateOf(
            (quickActions + EditorQuickAction.entries.map { it.id }).distinct().filter {
                EditorQuickAction.fromId(it) !=
                    null
            },
        )
    }
    val items = order.sortedBy { it !in quickActions }
    EditorToolbarList(
        items = items,
        activeCount = items.count { it in quickActions },
        onMove = { moved, activeCount ->
            order = moved
            change(moved.take(activeCount))
        },
        modifier = modifier,
        onDraggingChange = { active ->
            val wasDragging = dragging
            dragging = active
            if (wasDragging && !active) onChange(toolbar.copy(quickActions = quickActions))
            onDraggingChange(active)
        },
        header = {
            Text(
                stringResource(R.string.compose_editor_quick_actions_help),
                Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) { key, index, dragging, startDrag, move ->
        val item = requireNotNull(EditorQuickAction.fromId(key))
        val checked = key in quickActions
        EditorToolbarRow(
            item.icon,
            stringResource(item.label),
            checked,
            dragging,
            onCheckedChange = { shown ->
                val (selected, available) = items.filterNot { it == key }.partition { it in quickActions }
                order = selected + key + available
                val enabled = if (shown) quickActions + key else quickActions - key
                change(order.filter { it in enabled })
            },
            startDrag = startDrag,
            moveUp = if (index > 0 && (items[index - 1] in quickActions) == checked) ({ move(-1) }) else null,
            moveDown =
                if (index < items.lastIndex &&
                    (items[index + 1] in quickActions) == checked
                ) {
                    ({ move(1) })
                } else {
                    null
                },
        )
    }
}
