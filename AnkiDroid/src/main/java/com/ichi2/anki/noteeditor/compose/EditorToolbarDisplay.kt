// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import androidx.annotation.StringRes
import com.ichi2.anki.R

/** Device-local layout; individual fields still decide which buttons appear. */
enum class EditorToolbarDisplay(
    @StringRes val label: Int,
) {
    SCROLL(R.string.compose_editor_toolbar_scroll),
    STACK(R.string.compose_editor_toolbar_stack),
    HIDDEN(R.string.compose_editor_toolbar_hidden),
}
