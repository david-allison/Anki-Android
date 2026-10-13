// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ichi2.anki.CommonString

/** Native single-choice dialog with Material list spacing, opened by the metadata header. */
@Composable
internal fun NoteTypeDialog(
    title: String,
    choices: List<EditorChoice>?,
    selectedId: Long,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
) {
    if (choices == null) return
    val context = LocalContext.current
    val dismiss by rememberUpdatedState(onDismiss)
    val select by rememberUpdatedState(onSelect)
    DisposableEffect(context, choices, selectedId) {
        val dialog =
            MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setSingleChoiceItems(
                    choices.map { it.name }.toTypedArray(),
                    choices.indexOfFirst { it.id == selectedId },
                ) { picker, position ->
                    picker.dismiss()
                    select(choices[position].id)
                }.setNegativeButton(CommonString.dialog_cancel, null)
                .create()
        dialog.setOnDismissListener { dismiss() }
        dialog.show()
        onDispose {
            dialog.setOnDismissListener(null)
            dialog.dismiss()
        }
    }
}
