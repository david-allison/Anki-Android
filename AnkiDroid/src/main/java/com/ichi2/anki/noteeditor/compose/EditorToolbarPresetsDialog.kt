// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.common.utils.android.getColorFromAttr

@Composable
internal fun EditorToolbarPreset.displayName(): String =
    if (id == EditorToolbarPreset.DEFAULT_ID) stringResource(R.string.compose_editor_default_toolbar) else name

/** Shared management screen, also used to choose a preset for a particular field. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorToolbarPresetsDialog(
    presets: List<EditorToolbarPreset>,
    templates: List<EditorCustomToolbarButton>,
    onSavePreset: (EditorToolbarPreset) -> Unit,
    onDeletePreset: (String) -> Unit,
    onCustomButtons: () -> Unit,
    onDismiss: () -> Unit,
    target: EditorFieldTarget? = null,
    field: EditorFieldState? = null,
    noteTypeName: String = "",
    isCloze: Boolean = false,
    enabled: Boolean = true,
    onSelect: (String?) -> Unit = {},
    onSaveField: (EditorFieldToolbar) -> Unit = {},
) {
    val context = LocalContext.current
    val controlColor = remember(context) { Color(getColorFromAttr(context, android.R.attr.colorControlNormal)) }
    var editing by rememberSaveable { mutableStateOf<EditorToolbarPreset?>(null) }
    var naming by rememberSaveable { mutableStateOf<EditorToolbarPreset?>(null) }
    var deleting by rememberSaveable { mutableStateOf<EditorToolbarPreset?>(null) }
    var editField by rememberSaveable { mutableStateOf(false) }
    val defaultName = stringResource(R.string.compose_editor_default_toolbar)
    val fieldOnlyName = stringResource(R.string.compose_editor_field_only_toolbar)
    val baseToolbar = field?.toolbar ?: presets.first { it.id == EditorToolbarPreset.DEFAULT_ID }.applyTo(EditorFieldToolbar(), templates)
    if (editField && field != null && target != null) {
        EditorToolbarDialog(
            target = target,
            field = field,
            isCloze = isCloze,
            templates = templates,
            onCustomButtons = onCustomButtons,
            onDismiss = { editField = false },
            onSave = {
                onSaveField(it)
                editField = false
            },
            title = fieldOnlyName,
        )
        return
    }
    editing?.let { preset ->
        EditorToolbarDialog(
            target = EditorFieldTarget("preset:${preset.id}", 0, 0),
            field = EditorFieldState("", "", "", 20, false, null, false, false, false, preset.applyTo(EditorFieldToolbar(), templates)),
            // A shared preset can be used on cloze and non-cloze note types.
            isCloze = true,
            templates = templates,
            onCustomButtons = onCustomButtons,
            onDismiss = { editing = null },
            onSave = {
                onSavePreset(preset.withToolbar(it))
                editing = null
            },
            title = preset.displayName(),
            subtitle = stringResource(R.string.compose_editor_preset_scope),
        )
        return
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.compose_editor_toolbar_presets)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(painterResource(R.drawable.ic_baseline_arrow_back_24), stringResource(CommonString.close))
                        }
                    },
                )
            },
        ) { insets ->
            LazyColumn(Modifier.fillMaxSize().padding(insets).selectableGroup()) {
                if (field != null) {
                    item {
                        Text(
                            stringResource(R.string.compose_editor_preset_field, field.name, noteTypeName),
                            Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                item {
                    TextButton(
                        enabled = enabled,
                        onClick = { naming = EditorToolbarPreset(name = "").withToolbar(baseToolbar) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_add), null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.compose_editor_add_preset))
                    }
                }
                items(presets, key = { it.id }) { preset ->
                    val name = preset.displayName()
                    var menu by rememberSaveable(preset.id) { mutableStateOf(false) }
                    PresetRow(
                        enabled = enabled,
                        name = name,
                        selected = if (field != null) field.toolbarPresetId == preset.id else null,
                        description =
                            if (preset.id ==
                                EditorToolbarPreset.DEFAULT_ID
                            ) {
                                stringResource(R.string.compose_editor_default_preset_scope)
                            } else {
                                null
                            },
                        onClick = { if (field == null) editing = preset else onSelect(preset.id) },
                        onEdit = { editing = preset },
                        more = {
                            Box {
                                IconButton(onClick = { menu = true }, enabled = enabled) {
                                    Icon(
                                        painterResource(R.drawable.ic_more_vertical),
                                        stringResource(R.string.compose_editor_preset_options, name),
                                    )
                                }
                                DropdownMenu(
                                    expanded = menu,
                                    onDismissRequest = { menu = false },
                                    containerColor =
                                        MaterialTheme.colorScheme.surfaceContainer.compositeOver(
                                            MaterialTheme.colorScheme.surface,
                                        ),
                                ) {
                                    if (preset.id != EditorToolbarPreset.DEFAULT_ID) {
                                        DropdownMenuItem(
                                            leadingIcon = {
                                                Icon(
                                                    painterResource(R.drawable.ic_mode_edit_white),
                                                    null,
                                                    tint = controlColor,
                                                )
                                            },
                                            text = { Text(stringResource(CommonString.rename)) },
                                            onClick = {
                                                naming = preset
                                                menu =
                                                    false
                                            },
                                        )
                                    }
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            Icon(
                                                painterResource(R.drawable.baseline_content_copy_24),
                                                null,
                                                tint = controlColor,
                                            )
                                        },
                                        text = { Text(stringResource(R.string.compose_editor_duplicate_preset)) },
                                        onClick = {
                                            naming =
                                                preset.copy(
                                                    id =
                                                        java.util.UUID
                                                            .randomUUID()
                                                            .toString(),
                                                    name = "",
                                                )
                                            menu = false
                                        },
                                    )
                                    if (preset.id != EditorToolbarPreset.DEFAULT_ID) {
                                        DropdownMenuItem(
                                            leadingIcon = { Icon(painterResource(R.drawable.ic_delete), null, tint = controlColor) },
                                            text = { Text(stringResource(R.string.compose_editor_delete_preset)) },
                                            onClick = {
                                                deleting =
                                                    preset
                                                ; menu = false
                                            },
                                        )
                                    }
                                }
                            }
                        },
                    )
                }
                if (field != null) {
                    item {
                        PresetRow(
                            enabled = enabled,
                            name = fieldOnlyName,
                            selected = field.toolbarPresetId == null,
                            onClick = { onSelect(null) },
                            onEdit = { editField = true },
                        )
                    }
                }
            }
        }
    }
    naming?.let { preset ->
        var name by rememberSaveable(preset.id) { mutableStateOf(preset.name) }
        val duplicate =
            name.trim().equals(defaultName, ignoreCase = true) ||
                presets.any {
                    it.id != preset.id && it.name.equals(name.trim(), ignoreCase = true)
                }
        AlertDialog(
            onDismissRequest = { naming = null },
            title = {
                Text(
                    stringResource(if (presets.any { it.id == preset.id }) CommonString.rename else R.string.compose_editor_add_preset),
                )
            },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.compose_editor_preset_name)) },
                    singleLine = true,
                    isError = duplicate,
                    supportingText = if (duplicate) ({ Text(stringResource(R.string.compose_editor_preset_name_error)) }) else null,
                )
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && !duplicate, onClick = {
                    val updated = preset.copy(name = name.trim())
                    if (presets.any { it.id == preset.id }) onSavePreset(updated) else editing = updated
                    naming = null
                }) { Text(stringResource(CommonString.save)) }
            },
            dismissButton = { TextButton(onClick = { naming = null }) { Text(stringResource(CommonString.dialog_cancel)) } },
        )
    }
    deleting?.let { preset ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.compose_editor_delete_preset)) },
            text = { Text(stringResource(R.string.compose_editor_delete_preset_message, preset.name)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeletePreset(preset.id)
                    deleting = null
                }) { Text(stringResource(R.string.compose_editor_delete_preset)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(CommonString.dialog_cancel)) } },
        )
    }
}

@Composable
private fun PresetRow(
    name: String,
    selected: Boolean?,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    description: String? = null,
    enabled: Boolean = true,
    more: @Composable () -> Unit = { Spacer(Modifier.width(48.dp)) },
) {
    ListItem(
        content = { Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = description?.let { { Text(it) } },
        leadingContent = if (selected != null) ({ RadioButton(selected = selected, onClick = null) }) else null,
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEdit, enabled = enabled) {
                    Icon(painterResource(R.drawable.ic_mode_edit_white), stringResource(R.string.compose_editor_edit_preset, name))
                }
                more()
            }
        },
        modifier =
            Modifier.fillMaxWidth().then(
                if (selected != null) {
                    Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
                } else {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                },
            ),
    )
}
