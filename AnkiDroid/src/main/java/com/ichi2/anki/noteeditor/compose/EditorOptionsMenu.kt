// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.common.utils.android.getColorFromAttr
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
    onCustomButtons: () -> Unit,
    fieldOptionsEnabled: Boolean,
    onFieldOptions: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var fontSize by rememberSaveable { mutableStateOf<String?>(null) }
    var showToolbarDisplay by rememberSaveable { mutableStateOf(false) }
    if (showToolbarDisplay) {
        EditorToolbarDisplayDialog(
            selected = toolbarDisplay,
            onSelect = {
                onToolbarDisplay(it)
                showToolbarDisplay = false
            },
            onDismiss = { showToolbarDisplay = false },
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
        val context = LocalContext.current
        val controlColor = remember(context) { Color(getColorFromAttr(context, android.R.attr.colorControlNormal)) }
        val label = stringResource(R.string.compose_editor_options)
        EditorTooltip(label) {
            IconButton(
                onClick = { expanded = true },
                enabled = enabled,
                colors = IconButtonDefaults.iconButtonColors(contentColor = controlColor),
            ) { Icon(painterResource(R.drawable.ic_build_black_24), label) }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer.compositeOver(MaterialTheme.colorScheme.surface),
        ) {
            // Shrink Images, MathJax Preview and Auto-close HTML are omitted from the UI only.
            // These niche options are intended for future user-defined JS; keep their implementation.
            // Temporary rationale: https://discord.com/channels/368267295601983490/882228005504696350/1559393939734270055
            DropdownMenuItem(text = { Text(stringResource(CommonString.menu_font_size)) }, onClick = {
                expanded = false
                fontSize = (settings.fontSize ?: 20).toString()
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.compose_editor_toolbar_layout)) }, onClick = {
                expanded = false
                showToolbarDisplay = true
            })
            DropdownMenuItem(
                text = { Text(stringResource(R.string.compose_editor_custom_buttons)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_widgets), null) },
                onClick = {
                    expanded = false
                    onCustomButtons()
                },
            )
            if (toolbarDisplay != EditorToolbarDisplay.SCROLL) {
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
