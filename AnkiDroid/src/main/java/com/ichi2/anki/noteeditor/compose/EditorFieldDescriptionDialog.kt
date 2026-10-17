// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import androidx.core.text.htmlEncode
import com.ichi2.anki.CommonString
import com.ichi2.anki.R

@Composable
internal fun EditorFieldDescriptionDialog(
    field: EditorFieldState,
    noteTypeName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var description by rememberSaveable { mutableStateOf(field.description) }
    val dismiss = rememberEditorDismiss(description != field.description, onDismiss)
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.compose_editor_field_description)) },
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
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.compose_editor_field_description)) },
                    minLines = 3,
                    maxLines = 6,
                )
                Text(stringResource(R.string.compose_editor_field_description_help))
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(description) }) { Text(stringResource(CommonString.save)) }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(CommonString.dialog_cancel)) } },
    )
}
