// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.ichi2.anki.R
import com.ichi2.anki.noteeditor.web.WebEditorSettings

@Composable
internal fun EditorOptionsMenu(
    settings: WebEditorSettings,
    enabled: Boolean,
    onSettings: (WebEditorSettings) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = enabled) {
            Icon(painterResource(R.drawable.ic_settings_black), stringResource(R.string.compose_editor_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            fun change(value: WebEditorSettings) {
                expanded = false
                onSettings(value)
            }
            EditorOption(R.string.compose_editor_shrink_images, settings.shrinkImages) {
                change(settings.copy(shrinkImages = !settings.shrinkImages))
            }
            EditorOption(R.string.compose_editor_mathjax_preview, settings.mathjaxPreview) {
                change(settings.copy(mathjaxPreview = !settings.mathjaxPreview))
            }
            EditorOption(R.string.compose_editor_auto_close_tags, settings.autoCloseTags) {
                change(settings.copy(autoCloseTags = !settings.autoCloseTags))
            }
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
