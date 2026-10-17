// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.PopupProperties
import com.ichi2.anki.R

@Composable
internal fun EditorToolbarOverflowMenu(
    target: EditorFieldTarget?,
    enabled: Boolean,
    onFieldOptions: () -> Unit,
    onToolbar: (EditorFieldTarget) -> Unit,
    onQuickActions: (EditorFieldTarget) -> Unit,
) {
    // A menu opened for one field must not silently retarget after a field/document change.
    var expanded by remember(target, enabled) { mutableStateOf(false) }
    val controlColor = editorControlColor()
    Box {
        EditorActionButton(
            R.drawable.ic_more_vertical,
            androidx.appcompat.R.string.abc_action_menu_overflow_description,
            enabled && target != null,
        ) { expanded = true }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer.compositeOver(MaterialTheme.colorScheme.surface),
            // Retain keyboard navigation/Back in the menu without taking IME focus from the field.
            properties =
                PopupProperties(
                    flags = WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                ),
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.compose_editor_field_options)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_tune_white), null, tint = controlColor) },
                onClick = {
                    expanded = false
                    onFieldOptions()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.compose_editor_toolbar)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_toolbar), null, tint = controlColor) },
                onClick = {
                    expanded = false
                    target?.let(onToolbar)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.compose_editor_quick_actions)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_touch), null, tint = controlColor) },
                onClick = {
                    expanded = false
                    target?.let(onQuickActions)
                },
            )
        }
    }
}
