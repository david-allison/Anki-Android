// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.utils.openUrl

/** Edits the shared note-type font and size used for editing and typed answers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorFieldFontDialog(
    field: EditorFieldState,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit,
) {
    var font by rememberSaveable { mutableStateOf(field.font) }
    var size by rememberSaveable { mutableStateOf(field.fontSize.toString()) }
    var expanded by remember { mutableStateOf(false) }
    val dismiss = rememberEditorDismiss(font != field.font || size != field.fontSize.toString(), onDismiss)
    val valid = font.isNotBlank() && font.none { it.isISOControl() } && size.toIntOrNull() in 5..300
    val colors = MaterialTheme.colorScheme
    val menuColor = colors.surfaceContainer.compositeOver(colors.surface)
    AlertDialog(
        onDismissRequest = dismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.compose_editor_field_font), Modifier.weight(1f))
                val context = LocalContext.current
                EditorActionButton(
                    icon = R.drawable.help_outline,
                    label = CommonString.help,
                    enabled = true,
                    onClick = { context.openUrl(R.string.link_manual_customizing_fields) },
                )
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.compose_editor_field_font_help))
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    TextField(
                        value = font,
                        onValueChange = { font = it },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                        label = { Text(TR.fieldsFont()) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium,
                        shape = MaterialTheme.shapes.large,
                        colors =
                            TextFieldDefaults.colors(
                                focusedContainerColor = colors.surfaceContainerHigh.compositeOver(colors.surface),
                                unfocusedContainerColor = menuColor,
                                focusedTrailingIconColor = editorControlColor(),
                                unfocusedTrailingIconColor = editorControlColor(),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(
                                expanded = expanded,
                                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.SecondaryEditable),
                            )
                        },
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        shape = MaterialTheme.shapes.large,
                        containerColor = menuColor,
                    ) {
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
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(font.trim(), size.toInt()) }) { Text(stringResource(CommonString.save)) }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(CommonString.dialog_cancel)) } },
    )
}
