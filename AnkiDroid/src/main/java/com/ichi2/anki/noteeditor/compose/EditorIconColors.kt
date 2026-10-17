// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.ichi2.anki.R
import com.ichi2.anki.common.utils.android.getColorFromAttr

/** Match native controls and the WebView's field actions without muting text or selected states. */
@Composable
internal fun editorControlColor(): Color {
    val context = LocalContext.current
    return remember(context) { Color(getColorFromAttr(context, android.R.attr.colorControlNormal)) }
}

@Composable
internal fun editorIconButtonColors(contentColor: Color = editorControlColor()) =
    IconButtonDefaults.iconButtonColors(contentColor = contentColor)

/** The equation symbol's fine strokes need text contrast to avoid looking disabled. */
@Composable
internal fun editorToolbarIconColor(icon: Int): Color =
    if (icon == R.drawable.ic_add_equation_black_24dp) MaterialTheme.colorScheme.onSurface else editorControlColor()
