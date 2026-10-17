// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ichi2.anki.CommonString
import com.ichi2.anki.R

/** Edits the field's font size for editing and typed answers, preserving its configured font. */
@Composable
internal fun EditorFieldFontDialog(
    field: EditorFieldState,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit,
) {
    var size by rememberSaveable { mutableStateOf(field.fontSize.toString()) }
    val dismiss = rememberEditorDismiss(size != field.fontSize.toString(), onDismiss)
    val valid = size.toIntOrNull() in 5..300
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.compose_editor_field_font)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.compose_editor_field_font_help))
                // TODO: Restore font-name selection with verified WebView fonts and an explicit Custom option.
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
            TextButton(enabled = valid, onClick = { onSave(field.font, size.toInt()) }) { Text(stringResource(CommonString.save)) }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(CommonString.dialog_cancel)) } },
    )
}
