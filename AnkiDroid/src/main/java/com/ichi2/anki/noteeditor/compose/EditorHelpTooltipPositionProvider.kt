// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider

/** Keep the bubble aligned with Material 3's caret near a window edge. */
internal class EditorHelpTooltipPositionProvider(
    private val delegate: PopupPositionProvider,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val position = delegate.calculatePosition(anchorBounds, windowSize, layoutDirection, popupContentSize)
        val width = popupContentSize.width
        val centeredLeft = anchorBounds.center.x - width / 2
        // TODO: Remove when Material 3's caretX follows its popup's window-edge clamping.
        // In 1.5.0-beta01 the caret still expects start/end alignment with the anchor.
        // Clamping the bubble to the screen instead puts its caret over the adjacent X.
        val left =
            when {
                width >= windowSize.width -> 0
                centeredLeft < 0 -> anchorBounds.left.coerceAtMost(windowSize.width - width)
                centeredLeft + width > windowSize.width -> (anchorBounds.right - width).coerceAtLeast(0)
                else -> centeredLeft
            }
        return position.copy(x = left)
    }
}
