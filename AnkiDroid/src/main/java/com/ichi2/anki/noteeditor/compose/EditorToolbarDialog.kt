// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.view.MotionEvent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ichi2.anki.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorToolbarDialog(
    target: EditorFieldTarget,
    field: EditorFieldState,
    isCloze: Boolean,
    templates: List<EditorCustomToolbarButton>,
    onCustomButtons: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (EditorFieldToolbar) -> Unit,
    title: String? = null,
    subtitle: String? = null,
) {
    var pendingToolbar by rememberSaveable(target) { mutableStateOf(field.toolbar) }
    val dismiss = rememberEditorDismiss(pendingToolbar != field.toolbar, onDismiss)
    Dialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        EditorToolbarContent(
            pendingToolbar,
            { pendingToolbar = it },
            isCloze,
            templates,
            onCustomButtons,
            onSave,
            title,
            subtitle,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorToolbarContent(
    pendingToolbar: EditorFieldToolbar,
    onToolbarChange: (EditorFieldToolbar) -> Unit,
    isCloze: Boolean,
    templates: List<EditorCustomToolbarButton>,
    onCustomButtons: () -> Unit,
    onSave: (EditorFieldToolbar) -> Unit,
    title: String? = null,
    subtitle: String? = null,
    enabled: Boolean = true,
    buttonsEnabled: Boolean = enabled,
    header: @Composable () -> Unit = {},
    topBar: (@Composable () -> Unit)? = null,
    embedded: Boolean = false,
    scrollCustomButtons: Boolean = false,
    onDraggingChange: (Boolean) -> Unit = {},
) {
    // The shared library can change while this dialog stays open. Resolve its latest
    // definitions without discarding pending visibility/order changes for this field.
    val templatesById = templates.associateBy { it.id }
    val toolbar = pendingToolbar.copy(customButtons = pendingToolbar.customButtons.mapNotNull { templatesById[it.id] })
    val allItems = toolbar.orderedItems(templates)
    val items = allItems.filter { it.appliesTo(isCloze) }.sortedBy { !toolbar.isEnabled(it) }
    val enabledKeys = items.filter { toolbar.isEnabled(it) }.map { it.key }.toSet()
    val density = LocalDensity.current
    val buttonHeight = ButtonDefaults.MediumContainerHeight
    var customButtonsHeight by remember { mutableStateOf(buttonHeight) }
    var dragging by remember { mutableStateOf(false) }
    val customButtons: @Composable () -> Unit = {
        Button(
            onClick = onCustomButtons,
            enabled = buttonsEnabled,
            modifier =
                Modifier.heightIn(min = buttonHeight).onSizeChanged {
                    customButtonsHeight = with(density) { it.height.toDp() }
                },
            shapes = ButtonDefaults.shapesFor(buttonHeight),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            elevation = if (scrollCustomButtons) null else ButtonDefaults.buttonElevation(defaultElevation = 3.dp),
            contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight, hasStartIcon = true),
        ) {
            Icon(painterResource(R.drawable.ic_widgets), null, Modifier.size(ButtonDefaults.iconSizeFor(buttonHeight)))
            Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(buttonHeight)))
            Text(stringResource(R.string.compose_editor_custom_buttons), style = ButtonDefaults.textStyleFor(buttonHeight))
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = if (embedded) WindowInsets(0) else ScaffoldDefaults.contentWindowInsets,
        topBar = {
            if (topBar != null) {
                topBar()
            } else {
                TopAppBar(
                    title = {
                        Text(
                            title ?: stringResource(R.string.compose_editor_toolbar),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    actions = {
                        EditorSaveButton(onClick = { onSave(toolbar) }, enabled = enabled, modifier = Modifier.padding(end = 8.dp))
                    },
                )
            }
        },
        floatingActionButton = {
            // Leave the entire viewport available for dragging and edge auto-scroll.
            if (!scrollCustomButtons && !dragging) customButtons()
        },
    ) { insets ->
        EditorToolbarList(
            items = items.map { it.key },
            activeCount = enabledKeys.size,
            onDraggingChange = {
                dragging = it
                onDraggingChange(it)
            },
            // Keep this clearance while dragging so hiding the button never moves the list.
            bottomContentPadding = if (scrollCustomButtons) 0.dp else customButtonsHeight + 32.dp,
            onMove = { order, activeCount -> onToolbarChange(toolbar.withButtonLayout(order, activeCount, templates, isCloze)) },
            canMove = { buttonsEnabled },
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(insets)
                    .padding(horizontal = 16.dp)
                    .clipToBounds(),
            header = {
                Column {
                    header()
                    subtitle?.let { Text(it, Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyMedium) }
                    // At large text sizes above the IME, keep the action in the scrolling content.
                    if (scrollCustomButtons) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.CenterEnd) {
                            customButtons()
                        }
                    }
                }
            },
        ) { key, index, dragging, startDrag, move ->
            // RecyclerView retains a removed template's row through its exit animation.
            val item = items.firstOrNull { it.key == key } ?: return@EditorToolbarList
            val label =
                when (item) {
                    is EditorToolbarItem.Builtin -> stringResource(item.button.label)
                    is EditorToolbarItem.Custom -> item.button.label
                }
            val checked = toolbar.isEnabled(item)
            EditorToolbarRow(
                when (item) {
                    is EditorToolbarItem.Builtin -> item.button.icon
                    is EditorToolbarItem.Custom -> R.drawable.ic_widgets
                },
                label,
                checked,
                dragging,
                onCheckedChange = { shown ->
                    // A toggle crosses the boundary: append to enabled, or prepend to available.
                    val (enabled, available) = items.filterNot { it.key == key }.partition { toolbar.isEnabled(it) }
                    val displayed = (enabled + item + available).iterator()
                    val ordered =
                        toolbar.copy(
                            buttonOrder = allItems.map { if (it.appliesTo(isCloze)) displayed.next().key else it.key },
                        )
                    onToolbarChange(
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
                        },
                    )
                },
                enabled = buttonsEnabled,
                startDrag = startDrag,
                moveUp = if (index > 0 && toolbar.isEnabled(items[index - 1]) == checked) ({ move(-1) }) else null,
                moveDown = if (index < items.lastIndex && toolbar.isEnabled(items[index + 1]) == checked) ({ move(1) }) else null,
                supportingText = if (item is EditorToolbarItem.Custom) stringResource(R.string.compose_editor_custom_button) else null,
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun EditorToolbarRow(
    icon: Int,
    label: String,
    checked: Boolean,
    dragging: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    startDrag: () -> Unit,
    moveUp: (() -> Boolean)?,
    moveDown: (() -> Boolean)?,
    supportingText: String? = null,
    enabled: Boolean = true,
) {
    val controlColor = editorControlColor()
    val toggleLabel =
        stringResource(
            if (checked) R.string.compose_editor_remove_custom_button else R.string.compose_editor_add_toolbar_button,
            label,
        )
    val upLabel = stringResource(R.string.compose_editor_move_button_up, label)
    val downLabel = stringResource(R.string.compose_editor_move_button_down, label)
    val dragLabel = stringResource(R.string.compose_editor_reorder_button, label)
    Surface(
        color =
            if (checked) {
                MaterialTheme.colorScheme.primary
                    .copy(
                        alpha = 0.16f,
                    ).compositeOver(MaterialTheme.colorScheme.surface)
            } else {
                MaterialTheme.colorScheme.surface
            },
        tonalElevation = if (dragging) 4.dp else 0.dp,
    ) {
        Row(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.38f), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clickable(enabled = enabled, role = Role.Button, onClickLabel = toggleLabel) { onCheckedChange(!checked) }
                    .semantics { selected = checked },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.padding(start = 12.dp).size(20.dp)) {
                    Icon(painterResource(icon), contentDescription = null, tint = editorToolbarIconColor(icon))
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    supportingText?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(if (checked) R.drawable.ic_remove else R.drawable.ic_add),
                        contentDescription = null,
                        tint = controlColor,
                    )
                }
            }
            Box(
                Modifier
                    .size(48.dp)
                    .semantics {
                        contentDescription = dragLabel
                        if (!enabled) disabled()
                        customActions =
                            buildList {
                                if (enabled) {
                                    moveUp?.let { add(CustomAccessibilityAction(upLabel, it)) }
                                    moveDown?.let { add(CustomAccessibilityAction(downLabel, it)) }
                                }
                            }
                    }.pointerInteropFilter { event ->
                        if (enabled && event.actionMasked == MotionEvent.ACTION_DOWN) startDrag()
                        enabled
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_drag_indicator_24), contentDescription = null, tint = controlColor)
            }
        }
    }
}
