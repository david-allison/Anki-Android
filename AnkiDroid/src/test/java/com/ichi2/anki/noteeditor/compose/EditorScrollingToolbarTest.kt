// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EditorScrollingToolbarTest {
    private val icon = ToolbarButtonExtent(48, 24)
    private val mode = ToolbarButtonExtent(56, 52)
    private val defaultButtons = List(5) { icon } + mode + List(11) { icon }

    @Test
    fun `fitting buttons retain their spacing`() {
        assertEquals(0, toolbarPeekSpacing(List(7) { icon }, 400, 8))
        assertEquals(0, toolbarPeekSpacing(emptyList(), 400, 8))
    }

    @Test
    fun `existing Pixel peek retains its spacing`() {
        assertEquals(0, toolbarPeekSpacing(defaultButtons, 426, 8))
    }

    @Test
    fun `overflow exposes visible content across phone and tablet widths`() {
        val configurations =
            listOf(
                defaultButtons,
                List(18) { icon },
                List(3) { ToolbarButtonExtent(84, 60) } + defaultButtons,
                List(3) { ToolbarButtonExtent(58, 9) } + defaultButtons,
                listOf(mode) + defaultButtons,
            )
        for (buttons in configurations) {
            for (viewport in 240..800) {
                if (buttons.sumOf { it.width } + 16 <= viewport) continue
                val spacing = toolbarPeekSpacing(buttons, viewport, 8)
                var position = 8
                assertTrue(
                    buttons.any {
                        val left = position + (it.width - it.contentWidth) / 2f
                        val right = left + it.contentWidth
                        position += it.width + spacing
                        left < viewport && right > viewport
                    },
                    "No partial content at $viewport with $buttons and gap $spacing",
                )
                assertTrue(spacing in 0..20, "Keep inter-button spacing modest")
            }
        }
    }
}
