// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
import org.junit.Test
import kotlin.test.assertEquals

class EditorHelpTooltipPositionProviderTest {
    // Material 3 centers the bubble, then clamps it to the window's edges.
    private val materialPosition =
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset =
                IntOffset(
                    (anchorBounds.center.x - popupContentSize.width / 2).coerceIn(
                        0,
                        (windowSize.width - popupContentSize.width).coerceAtLeast(0),
                    ),
                    123,
                )
        }

    private val provider = EditorHelpTooltipPositionProvider(materialPosition)

    @Test
    fun `right-edge help aligns with the info button rather than the close button`() {
        assertEquals(IntOffset(34, 123), position(IntRect(306, 400, 354, 448), LayoutDirection.Ltr))
    }

    @Test
    fun `left-edge help aligns with the info button in RTL`() {
        assertEquals(IntOffset(56, 123), position(IntRect(56, 400, 104, 448), LayoutDirection.Rtl))
    }

    @Test
    fun `centered help retains Material positioning`() {
        assertEquals(IntOffset(44, 123), position(IntRect(180, 400, 228, 448), LayoutDirection.Ltr))
    }

    @Test
    fun `full-width help stays within the window`() {
        for (direction in LayoutDirection.entries) {
            assertEquals(IntOffset(0, 123), position(IntRect(306, 400, 354, 448), direction, popupWidth = 410))
        }
    }

    private fun position(
        anchor: IntRect,
        direction: LayoutDirection,
        popupWidth: Int = 320,
    ): IntOffset = provider.calculatePosition(anchor, IntSize(410, 900), direction, IntSize(popupWidth, 300))
}
