// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ichi2.anki.CommonString
import com.ichi2.anki.R

@Composable
internal fun EditorRemovedFieldsDialog(
    fields: List<EditorRemovedField>,
    onDiscard: () -> Unit,
    onKeepDraft: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onKeepDraft,
        title = { Text(stringResource(R.string.compose_editor_removed_fields)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.compose_editor_removed_fields_explanation))
                LazyColumn(Modifier.heightIn(max = 240.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(fields) { field ->
                        Column {
                            Text(field.name, style = MaterialTheme.typography.titleSmall)
                            SelectionContainer { Text(field.html) }
                        }
                    }
                }
                TextButton(onClick = {
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(
                        ClipData.newPlainText(
                            context.getString(R.string.compose_editor_removed_fields),
                            fields.joinToString("\n\n") { "${it.name}\n${it.html}" },
                        ),
                    )
                }) { Text(stringResource(android.R.string.copy)) }
            }
        },
        confirmButton = { TextButton(onClick = onDiscard) { Text(stringResource(CommonString.discard)) } },
        dismissButton = { TextButton(onClick = onKeepDraft) { Text(stringResource(R.string.compose_editor_keep_draft_close)) } },
    )
}
