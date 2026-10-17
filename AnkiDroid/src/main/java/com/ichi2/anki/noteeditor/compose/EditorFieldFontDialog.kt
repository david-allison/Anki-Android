// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.text.htmlEncode
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CommonString
import com.ichi2.anki.R

/** Edits note-type properties, not card CSS or the device-wide readability override. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorFieldFontDialog(
    field: EditorFieldState,
    noteTypeName: String,
    fontSizeOverride: Int?,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit,
) {
    var font by rememberSaveable { mutableStateOf(field.font) }
    var size by rememberSaveable { mutableStateOf(field.fontSize.toString()) }
    var expanded by remember { mutableStateOf(false) }
    val valid = font.isNotBlank() && font.none { it.isISOControl() } && size.toIntOrNull() in 5..300
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.compose_editor_field_font)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    AnnotatedString.fromHtml(
                        stringResource(
                            R.string.compose_editor_field_options_help_scope,
                            "<b>${field.name.htmlEncode()}</b>",
                            "<b>${noteTypeName.htmlEncode()}</b>",
                        ),
                    ),
                )
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = font,
                        onValueChange = { font = it },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                        label = { Text(TR.fieldsFont()) },
                        singleLine = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(
                                expanded = expanded,
                                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.SecondaryEditable),
                            )
                        },
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf("Arial", "sans-serif", "serif", "monospace").forEach { name ->
                            DropdownMenuItem(text = { Text(name) }, onClick = {
                                font = name
                                expanded = false
                            })
                        }
                    }
                }
                OutlinedTextField(
                    value = size,
                    onValueChange = { if (it.length <= 3 && it.all(Char::isDigit)) size = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(CommonString.menu_font_size)) },
                    supportingText = { Text(stringResource(R.string.compose_editor_field_font_size_range)) },
                    isError = size.isNotEmpty() && size.toIntOrNull() !in 5..300,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text(stringResource(R.string.compose_editor_field_font_help))
                if (fontSizeOverride != null) {
                    Text(stringResource(R.string.compose_editor_field_font_override, fontSizeOverride))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(font.trim(), size.toInt()) }) { Text(stringResource(CommonString.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CommonString.dialog_cancel)) } },
    )
}
