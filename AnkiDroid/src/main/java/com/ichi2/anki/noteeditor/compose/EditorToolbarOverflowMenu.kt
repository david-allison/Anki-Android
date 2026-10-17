// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.os.Build
import android.view.WindowManager
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.DropdownMenuPopupPositionProvider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorPosition
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuGroupShapes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
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
    EditorOverflowBackHandler(expanded) { expanded = false }
    val controlColor = editorControlColor()
    Box {
        EditorActionButton(
            R.drawable.ic_more_vertical,
            androidx.appcompat.R.string.abc_action_menu_overflow_description,
            enabled && target != null,
        ) { expanded = true }
        DropdownMenuPopup(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            popupPositionProvider = rememberEditorOverflowPositionProvider(),
            // Keep input focus in the field while the menu is open, including with a visible IME.
            properties =
                PopupProperties(
                    flags =
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                            WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                ),
        ) {
            DropdownMenuGroup(
                shapes = MenuGroupShapes(MenuDefaults.shape, MenuDefaults.shape),
                modifier = Modifier.verticalScroll(rememberScrollState()),
                containerColor = MaterialTheme.colorScheme.surfaceContainer.compositeOver(MaterialTheme.colorScheme.surface),
                contentPadding = PaddingValues(vertical = 8.dp),
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
}

@Composable
private fun rememberEditorOverflowPositionProvider(): DropdownMenuPopupPositionProvider {
    val delegate = MenuDefaults.rememberDropdownMenuPopupPositionProvider(MenuAnchorPosition.Above)
    val topInset = WindowInsets.statusBars.getTop(LocalDensity.current)
    return remember(delegate, topInset) {
        object : DropdownMenuPopupPositionProvider by delegate {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset =
                delegate.calculatePosition(
                    anchorBounds,
                    // Match ExposedDropdownMenu's b/256233441 workaround: Popup's visible
                    // height excludes the status bar, but its anchor coordinates include it.
                    IntSize(windowSize.width, windowSize.height + topInset),
                    layoutDirection,
                    popupContentSize,
                )
        }
    }
}

/** A non-focusable popup cannot receive Back; handle it in the editor's window instead. */
@Composable
private fun EditorOverflowBackHandler(
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
