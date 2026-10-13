// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose.preview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.common.utils.android.getColorFromAttr

/** Keep the field WebView mounted underneath; preview only exists while explicitly opened. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorPreviewDialog(
    onDismiss: () -> Unit,
    preview: @Composable (Modifier) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        val background = Color(getColorFromAttr(LocalContext.current, R.attr.alternativeBackgroundColor))
        val lightBars = background.luminance() > 0.5f
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = lightBars
                    isAppearanceLightNavigationBars = lightBars
                }
            }
        }
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = background,
            contentColor = MaterialTheme.colorScheme.onSurface,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = background, scrolledContainerColor = background),
                    title = { Text(stringResource(CommonString.card_editor_preview_card)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                painterResource(R.drawable.ic_baseline_arrow_back_24),
                                stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
                            )
                        }
                    },
                )
            },
        ) { padding -> preview(Modifier.fillMaxSize().padding(padding)) }
    }
}
