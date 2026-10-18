// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.os.Build
import android.view.WindowManager
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.PopupProperties
import com.ichi2.anki.R

@Composable
internal fun EditorOptionsMenu(
    enabled: Boolean,
    onFields: () -> Unit,
    onCards: () -> Unit,
    toolbarDisplay: EditorToolbarDisplay,
    onToolbarDisplay: (EditorToolbarDisplay) -> Unit,
    fieldTarget: EditorFieldTarget?,
    onFieldOptions: () -> Unit,
) {
    // Never let a menu opened for one field silently target another field or document.
    var expanded by remember(fieldTarget, enabled) { mutableStateOf(false) }
    EditorMenuBackHandler(expanded) { expanded = false }
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
    Box {
        val controlColor = editorControlColor()
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
            // Keep the field's input connection and caret while browsing settings.
            properties =
                PopupProperties(
                    flags =
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                            WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                ),
        ) {
            // Shrink Images, MathJax Preview and Auto-close HTML are omitted from the UI only.
            // These niche options are intended for future user-defined JS; keep their implementation.
            // Temporary rationale: https://discord.com/channels/368267295601983490/882228005504696350/1559393939734270055
            // One settings entry point for every toolbar layout.
            EditorSettingsMenuItem(
                label = R.string.compose_editor_field_options,
                icon = R.drawable.ic_tune_white,
                enabled = fieldTarget != null,
            ) {
                expanded = false
                onFieldOptions()
            }
            HorizontalDivider()
            // Device-wide presentation settings, separate from shared field and preset properties.
            EditorSettingsMenuItem(R.string.compose_editor_toolbar_layout, R.drawable.ic_view_column) {
                expanded = false
                showToolbarDisplay = true
            }
            HorizontalDivider()
            // Note-type structure and card templates.
            EditorSettingsMenuItem(R.string.compose_editor_fields, R.drawable.ic_list_black) {
                expanded = false
                onFields()
            }
            EditorSettingsMenuItem(R.string.compose_editor_cards, R.drawable.ic_flashcard_black) {
                expanded = false
                onCards()
            }
        }
    }
}

@Composable
private fun EditorSettingsMenuItem(
    label: Int,
    icon: Int,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = { Icon(painterResource(icon), null) },
        colors = MenuDefaults.itemColors(leadingIconColor = editorControlColor()),
        enabled = enabled,
        onClick = onClick,
    )
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

/** A non-focusable popup cannot receive Back; handle it in the editor's window instead. */
@Composable
private fun EditorMenuBackHandler(
    expanded: Boolean,
    onDismiss: () -> Unit,
) {
    BackHandler(enabled = expanded, onBack = onDismiss)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val view = LocalView.current
        val dismiss by rememberUpdatedState(onDismiss)
        DisposableEffect(view, expanded) {
            val dispatcher = view.findOnBackInvokedDispatcher()
            val callback = OnBackInvokedCallback { dismiss() }
            if (expanded) {
                // Match Popup's priority so Back dismisses the menu before the visible keyboard.
                dispatcher?.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY, callback)
            }
            onDispose { dispatcher?.unregisterOnBackInvokedCallback(callback) }
        }
    }
}
