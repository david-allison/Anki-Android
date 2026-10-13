// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.noteeditor.web.WebEditorSettings

@Composable
internal fun EditorOptionsMenu(
    settings: WebEditorSettings,
    enabled: Boolean,
    onSettings: (WebEditorSettings) -> Unit,
    onFields: () -> Unit,
    onCards: () -> Unit,
    toolbarDisplay: EditorToolbarDisplay,
    onToolbarDisplay: (EditorToolbarDisplay) -> Unit,
    fieldOptionsEnabled: Boolean,
    onFieldOptions: () -> Unit,
    onImageOcclusion: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var fontSize by rememberSaveable { mutableStateOf<String?>(null) }
    var showToolbarDisplay by rememberSaveable { mutableStateOf(false) }
    if (showToolbarDisplay) {
        AlertDialog(
            onDismissRequest = { showToolbarDisplay = false },
            title = { Text(stringResource(R.string.compose_editor_toolbar)) },
            text = {
                Column {
                    EditorToolbarDisplay.entries.forEach { display ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = display == toolbarDisplay,
                                    role = Role.RadioButton,
                                    onClick = {
                                        onToolbarDisplay(display)
                                        showToolbarDisplay = false
                                    },
                                ).padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = display == toolbarDisplay, onClick = null)
                            Text(stringResource(display.label), Modifier.padding(start = 16.dp))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showToolbarDisplay = false }) { Text(stringResource(CommonString.dialog_cancel)) } },
        )
    }
    fontSize?.let { value ->
        AlertDialog(
            onDismissRequest = { fontSize = null },
            title = { Text(stringResource(CommonString.menu_font_size)) },
            text = {
                OutlinedTextField(
                    value = value,
                    onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) fontSize = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(enabled = value.toIntOrNull() in 1..99, onClick = {
                    onSettings(settings.copy(fontSize = value.toInt()))
                    fontSize = null
                }) { Text(stringResource(CommonString.dialog_ok)) }
            },
            dismissButton = { TextButton(onClick = { fontSize = null }) { Text(stringResource(CommonString.dialog_cancel)) } },
        )
    }
    Box {
        EditorActionButton(R.drawable.ic_settings_black, R.string.compose_editor_options, enabled) { expanded = true }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer.compositeOver(MaterialTheme.colorScheme.surface),
        ) {
            // Shrink Images, MathJax Preview and Auto-close HTML are omitted from the UI only.
            // These niche options are intended for future user-defined JS; keep their implementation.
            // Temporary rationale: https://discord.com/channels/368267295601983490/882228005504696350/1559393939734270055
            onImageOcclusion?.let { openImageOcclusion ->
                DropdownMenuItem(text = { Text(stringResource(R.string.image_occlusion)) }, onClick = {
                    expanded = false
                    openImageOcclusion()
                })
            }
            DropdownMenuItem(text = { Text(stringResource(CommonString.menu_font_size)) }, onClick = {
                expanded = false
                fontSize = (settings.fontSize ?: 20).toString()
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.compose_editor_toolbar)) }, onClick = {
                expanded = false
                showToolbarDisplay = true
            })
            if (toolbarDisplay == EditorToolbarDisplay.HIDDEN) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.compose_editor_field_options)) },
                    enabled = fieldOptionsEnabled,
                    onClick = {
                        expanded = false
                        onFieldOptions()
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(text = { Text(stringResource(R.string.compose_editor_fields)) }, onClick = {
                expanded = false
                onFields()
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.compose_editor_cards)) }, onClick = {
                expanded = false
                onCards()
            })
        }
    }
}

@Composable
private fun EditorOption(
    label: Int,
    checked: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = { Checkbox(checked = checked, onCheckedChange = null) },
        onClick = onClick,
    )
}
