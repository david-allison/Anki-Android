// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.view.MotionEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ichi2.anki.CommonString
import com.ichi2.anki.R

@OptIn(ExperimentalMaterial3Api::class)
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
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.compose_editor_toolbar)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(painterResource(R.drawable.ic_clear_white), stringResource(CommonString.dialog_cancel))
                        }
                    },
                    actions = {
                        TextButton(onClick = { onSave(toolbar) }) { Text(stringResource(CommonString.save)) }
                    },
                )
            },
        ) { insets ->
            EditorToolbarList(
                items = items.map { it.key },
                onMove = { from, to -> toolbar = toolbar.moveButtonTo(from, to, templates, isCloze) },
                modifier = Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp),
                header = {
                    Column {
                        Text(field.name, style = MaterialTheme.typography.titleMedium)
                        Text(noteTypeName, style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.compose_editor_toolbar_scope), Modifier.padding(vertical = 12.dp))
                    }
                },
            ) { key, index, dragging, startDrag, move ->
                val item = items.first { it.key == key }
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
                EditorToolbarRow(
                    (item as? EditorToolbarItem.Builtin)?.button?.icon,
                    label,
                    checked,
                    dragging,
                    onCheckedChange = { shown ->
                        val ordered = toolbar.copy(buttonOrder = allItems.map { it.key })
                        toolbar =
                            when (item) {
                                is EditorToolbarItem.Builtin ->
                                    ordered.copy(
                                        hiddenButtons = if (shown) toolbar.hiddenButtons - item.key else toolbar.hiddenButtons + item.key,
                                    )
                                is EditorToolbarItem.Custom ->
                                    ordered.copy(
                                        customButtons =
                                            if (shown) {
                                                toolbar.customButtons + item.button
                                            } else {
                                                toolbar.customButtons.filterNot {
                                                    it.id ==
                                                        item.button.id
                                                }
                                            },
                                    )
                            }
                    },
                    startDrag = startDrag,
                    moveUp = if (index > 0) ({ move(-1) }) else null,
                    moveDown = if (index < items.lastIndex) ({ move(1) }) else null,
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun EditorToolbarRow(
    icon: Int?,
    label: String,
    checked: Boolean,
    dragging: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    startDrag: () -> Unit,
    moveUp: (() -> Boolean)?,
    moveDown: (() -> Boolean)?,
) {
    val upLabel = stringResource(R.string.compose_editor_move_button_up, label)
    val downLabel = stringResource(R.string.compose_editor_move_button_down, label)
    val dragLabel = stringResource(R.string.compose_editor_reorder_button, label)
    Surface(tonalElevation = if (dragging) 4.dp else 0.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).heightIn(min = 48.dp).toggleable(checked, role = Role.Checkbox, onValueChange = onCheckedChange),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked, onCheckedChange = null)
                Box(Modifier.padding(start = 12.dp).size(20.dp)) {
                    if (icon != null) Icon(painterResource(icon), contentDescription = null)
                }
                Text(label, Modifier.padding(start = 12.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Box(
                Modifier
                    .size(48.dp)
                    .semantics {
                        contentDescription = dragLabel
                        customActions =
                            buildList {
                                moveUp?.let { add(CustomAccessibilityAction(upLabel, it)) }
                                moveDown?.let { add(CustomAccessibilityAction(downLabel, it)) }
                            }
                    }.pointerInteropFilter { event ->
                        if (event.actionMasked == MotionEvent.ACTION_DOWN) startDrag()
                        true
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_drag_indicator_24), contentDescription = null)
            }
        }
    }
}
