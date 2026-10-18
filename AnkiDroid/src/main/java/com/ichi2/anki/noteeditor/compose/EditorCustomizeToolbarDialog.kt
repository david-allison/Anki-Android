// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.ui.internationalization.trCase
import java.util.UUID

@Composable
internal fun EditorToolbarPreset.displayName(): String =
    if (id == EditorToolbarPreset.DEFAULT_ID) stringResource(R.string.compose_editor_default_toolbar) else name

/** Field assignment and shared buttons in one screen, with all changes pending until Save. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorCustomizeToolbarDialog(
    presets: List<EditorToolbarPreset>,
    templates: List<EditorCustomToolbarButton>,
    field: EditorFieldState,
    noteTypeName: String,
    onSave: (EditorToolbarConfiguration) -> Unit,
    onCustomButtons: () -> Unit,
    onDismiss: () -> Unit,
    enabled: Boolean = true,
    toolbarHiddenGlobally: Boolean = false,
) {
    var pending by rememberSaveable {
        mutableStateOf(EditorToolbarConfiguration(presets, field.toolbarPresetId, field.toolbar.showToolbar))
    }
    val initial = rememberSaveable { EditorToolbarConfiguration(presets, field.toolbarPresetId, field.toolbar.showToolbar) }
    val dismiss = rememberEditorDismiss(pending != initial, onDismiss)
    Dialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        EditorCustomizeToolbarContent(
            pending = pending,
            onChange = { pending = it },
            templates = templates,
            field = field,
            noteTypeName = noteTypeName,
            onSave = onSave,
            onCustomButtons = onCustomButtons,
            enabled = enabled,
            toolbarHiddenGlobally = toolbarHiddenGlobally,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorCustomizeToolbarContent(
    pending: EditorToolbarConfiguration,
    onChange: (EditorToolbarConfiguration) -> Unit,
    templates: List<EditorCustomToolbarButton>,
    field: EditorFieldState,
    noteTypeName: String,
    onSave: (EditorToolbarConfiguration) -> Unit,
    onCustomButtons: () -> Unit,
    enabled: Boolean = true,
    toolbarHiddenGlobally: Boolean = false,
    topBar: (@Composable () -> Unit)? = null,
    embedded: Boolean = false,
    onDraggingChange: (Boolean) -> Unit = {},
) {
    var naming by rememberSaveable { mutableStateOf<EditorToolbarPreset?>(null) }
    var deleting by rememberSaveable { mutableStateOf<EditorToolbarPreset?>(null) }
    val selected = pending.presets.first { it.id == pending.presetId }
    val defaultName = stringResource(R.string.compose_editor_default_toolbar)

    fun updatePreset(updated: EditorToolbarPreset) {
        onChange(pending.copy(presets = pending.presets.map { if (it.id == updated.id) updated else it }))
    }
    EditorToolbarContent(
        pendingToolbar = selected.applyTo(EditorFieldToolbar(), templates),
        onToolbarChange = { updatePreset(selected.withToolbar(it)) },
        // A shared preset can also be used on cloze note types.
        isCloze = true,
        templates = templates,
        onCustomButtons = onCustomButtons,
        onSave = {
            // Template definitions may have changed while their library was open.
            onSave(pending.copy(presets = pending.presets.map { it.withToolbar(it.applyTo(EditorFieldToolbar(), templates)) }))
        },
        topBar = topBar,
        embedded = embedded,
        onDraggingChange = onDraggingChange,
        enabled = enabled,
        buttonsEnabled = enabled && pending.showToolbar,
        header = {
            Column(Modifier.padding(horizontal = 12.dp).padding(top = if (embedded) 12.dp else 0.dp)) {
                if (!embedded) {
                    Text(
                        stringResource(R.string.compose_editor_preset_field, field.name, noteTypeName),
                        Modifier.padding(vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ToolbarVisibilitySwitch(
                    checked = pending.showToolbar,
                    enabled = enabled,
                    onCheckedChange = { onChange(pending.copy(showToolbar = it)) },
                )
                if (toolbarHiddenGlobally) {
                    Text(
                        stringResource(R.string.compose_editor_toolbar_hidden_globally),
                        Modifier.padding(vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ToolbarPresetSelector(
                    presets = pending.presets,
                    selected = selected,
                    enabled = enabled && pending.showToolbar,
                    onSelect = { onChange(pending.copy(presetId = it)) },
                    onAdd = { naming = EditorToolbarPreset(name = "") },
                    onRename = { naming = selected },
                    onDuplicate = { naming = selected.copy(id = UUID.randomUUID().toString(), name = "") },
                    onDelete = { deleting = selected },
                )
                Text(
                    stringResource(R.string.compose_editor_preset_scope),
                    Modifier.padding(top = 12.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
    naming?.let { preset ->
        var name by rememberSaveable(preset.id) { mutableStateOf(preset.name) }
        val dismissName = rememberEditorDismiss(name != preset.name) { naming = null }
        val nameFocus = remember(preset.id) { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current
        val exists = pending.presets.any { it.id == preset.id }
        val duplicate =
            name.isNotBlank() && (
                name.trim().equals(defaultName, ignoreCase = true) ||
                    pending.presets.any { it.id != preset.id && it.name.equals(name.trim(), ignoreCase = true) }
            )
        AlertDialog(
            onDismissRequest = dismissName,
            title = { Text(stringResource(if (exists) CommonString.rename else R.string.compose_editor_add_preset)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.focusRequester(nameFocus),
                    label = { Text(stringResource(R.string.compose_editor_preset_name)) },
                    singleLine = true,
                    isError = duplicate,
                    supportingText = if (duplicate) ({ Text(stringResource(R.string.compose_editor_preset_name_error)) }) else null,
                )
            },
            confirmButton = {
                TextButton(enabled = enabled && name.isNotBlank() && !duplicate, onClick = {
                    val updated = preset.copy(name = name.trim())
                    if (exists) {
                        updatePreset(updated)
                    } else {
                        onChange(pending.copy(presets = pending.presets + updated, presetId = updated.id))
                    }
                    naming = null
                }) { Text(stringResource(CommonString.dialog_ok)) }
            },
            dismissButton = { TextButton(onClick = dismissName) { Text(stringResource(CommonString.dialog_cancel)) } },
        )
        LaunchedEffect(preset.id) {
            nameFocus.requestFocus()
            keyboard?.show()
        }
    }
    deleting?.let { preset ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.compose_editor_delete_preset)) },
            text = { Text(stringResource(R.string.compose_editor_delete_preset_message, preset.name)) },
            confirmButton = {
                TextButton(enabled = enabled, onClick = {
                    onChange(
                        pending.copy(
                            presets = pending.presets.filterNot { it.id == preset.id },
                            presetId = EditorToolbarPreset.DEFAULT_ID,
                        ),
                    )
                    deleting = null
                }) { Text(stringResource(R.string.compose_editor_delete_preset)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(CommonString.dialog_cancel)) } },
        )
    }
}

/** A master switch for the configuration below it; the entire rounded row is one switch target. */
@Composable
private fun ToolbarVisibilitySwitch(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                .heightIn(min = 72.dp)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.compose_editor_show_field_toolbar),
                Modifier.weight(1f).alpha(if (enabled) 1f else 0.38f),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.width(16.dp))
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(checkedIconColor = MaterialTheme.colorScheme.primary),
                thumbContent =
                    if (checked) {
                        { Icon(painterResource(R.drawable.ic_done), null, Modifier.size(SwitchDefaults.IconSize)) }
                    } else {
                        null
                    },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolbarPresetSelector(
    presets: List<EditorToolbarPreset>,
    selected: EditorToolbarPreset,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    var options by rememberSaveable { mutableStateOf(false) }
    val controlColor = editorControlColor()
    val menuColor = MaterialTheme.colorScheme.surfaceContainer.compositeOver(MaterialTheme.colorScheme.surface)
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        ExposedDropdownMenuBox(
            expanded = choosing,
            onExpandedChange = { if (enabled) choosing = it },
            modifier = Modifier.weight(1f),
        ) {
            TextField(
                value = selected.displayName(),
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                label = { Text(stringResource(R.string.compose_editor_preset)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = choosing) },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium,
                shape = MaterialTheme.shapes.large,
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor =
                            MaterialTheme.colorScheme.surfaceContainerHigh.compositeOver(
                                MaterialTheme.colorScheme.surface,
                            ),
                        unfocusedContainerColor = menuColor,
                        disabledContainerColor = menuColor,
                        focusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedTrailingIconColor = controlColor,
                        unfocusedTrailingIconColor = controlColor,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                modifier =
                    Modifier.fillMaxWidth().menuAnchor(
                        ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                        enabled,
                    ),
            )
            ExposedDropdownMenu(
                expanded = choosing,
                onDismissRequest = { choosing = false },
                containerColor = menuColor,
                shape = MaterialTheme.shapes.large,
            ) {
                presets.forEach { preset ->
                    DropdownMenuItem(
                        text = { Text(preset.displayName(), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        trailingIcon = {
                            if (preset.id == selected.id) Icon(painterResource(R.drawable.ic_done_white), null, tint = controlColor)
                        },
                        modifier = Modifier.semantics { this.selected = preset.id == selected.id },
                        onClick = {
                            onSelect(preset.id)
                            choosing = false
                        },
                    )
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        Box {
            IconButton(onClick = { options = true }, enabled = enabled, colors = editorIconButtonColors()) {
                Icon(
                    painterResource(R.drawable.ic_more_vertical),
                    stringResource(R.string.compose_editor_preset_options, selected.displayName()),
                )
            }
            DropdownMenu(expanded = options, onDismissRequest = { options = false }, containerColor = menuColor) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.compose_editor_add_preset)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_add), null, tint = controlColor) },
                    onClick = {
                        options = false
                        onAdd()
                    },
                )
                HorizontalDivider()
                if (selected.id != EditorToolbarPreset.DEFAULT_ID) {
                    DropdownMenuItem(
                        text = { Text(stringResource(CommonString.rename)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_edit_note), null, tint = controlColor) },
                        onClick = {
                            options = false
                            onRename()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(trCase().clonePreset()) },
                    leadingIcon = { Icon(painterResource(R.drawable.baseline_content_copy_24), null, tint = controlColor) },
                    enabled = enabled,
                    onClick = {
                        options = false
                        onDuplicate()
                    },
                )
                if (selected.id != EditorToolbarPreset.DEFAULT_ID) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.compose_editor_delete_preset)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_delete), null, tint = controlColor) },
                        onClick = {
                            options = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}
