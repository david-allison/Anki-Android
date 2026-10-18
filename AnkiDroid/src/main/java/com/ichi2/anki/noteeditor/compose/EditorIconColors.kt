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

/** Letterforms and equations need text contrast; other controls retain the native icon tint. */
@Composable
internal fun editorToolbarIconColor(icon: Int): Color =
    when (icon) {
        R.drawable.ic_format_bold_black_24dp,
        R.drawable.ic_format_italic_black_24dp,
        R.drawable.ic_format_underlined_black_24dp,
        R.drawable.ic_editor_format_superscript,
        R.drawable.ic_editor_format_subscript,
        R.drawable.ic_editor_format_color_text,
        R.drawable.ic_add_equation_black_24dp,
        -> MaterialTheme.colorScheme.onSurface
        else -> editorControlColor()
    }
