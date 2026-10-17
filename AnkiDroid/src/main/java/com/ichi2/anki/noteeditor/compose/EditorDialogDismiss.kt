// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CommonString
import com.ichi2.anki.ui.internationalization.sentenceCase

/** Keep the editing dialog mounted so declining a discard retains its pending values. */
@Composable
internal fun rememberEditorDismiss(
    hasChanges: Boolean,
    onDismiss: () -> Unit,
): () -> Unit {
    var confirming by rememberSaveable { mutableStateOf(false) }
    if (confirming) {
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { confirming = false },
            text = { Text(TR.cardTemplatesDiscardChanges()) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onDismiss()
                }) { Text(stringResource(CommonString.discard)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(with(context) { TR.sentenceCase.keepEditing }) }
            },
        )
    }
    return { if (hasChanges) confirming = true else onDismiss() }
}
