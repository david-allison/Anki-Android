// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditorToolbarOrderTest {
    private val ruby = EditorCustomToolbarButton(id = "ruby", label = "Ruby", prefix = "<ruby>", suffix = "</ruby>")
    private val rubyKey = "custom:ruby"

    @Test
    fun `unconfigured toolbars keep their existing custom then Anki order`() {
        val toolbar = EditorFieldToolbar(customButtons = listOf(ruby))
        assertEquals(listOf(rubyKey) + EditorToolbarButton.entries.map { it.name }, toolbar.orderedItems().map { it.key })
    }

    @Test
    fun `saved order interleaves both kinds and tolerates missing and duplicated buttons`() {
        val toolbar =
            EditorFieldToolbar(customButtons = listOf(ruby), buttonOrder = listOf("ITALIC", rubyKey, "BOLD", "BOLD", "custom:deleted"))
        val keys = toolbar.orderedItems().map { it.key }
        assertEquals(listOf("ITALIC", rubyKey, "BOLD", "UNDERLINE"), keys.take(4))
        assertEquals(EditorToolbarButton.entries.size + 1, keys.size)
    }

    @Test
    fun `dropping an available template into Active enables it at its new position`() {
        val toolbar = EditorFieldToolbar()
        val visible = toolbar.orderedItems(listOf(ruby)).filter { it.appliesTo(false) }.map { it.key }
        val order = listOf("BOLD", "ITALIC", rubyKey) + visible.filterNot { it in setOf("BOLD", "ITALIC", rubyKey) }
        val moved = toolbar.withButtonLayout(order, order.size, listOf(ruby), false)
        assertEquals(listOf("BOLD", "ITALIC", rubyKey, "UNDERLINE"), moved.buttonOrder.take(4))
        assertEquals(listOf(ruby), moved.customButtons)
        assertEquals(emptySet(), moved.hiddenButtons)
    }

    @Test
    fun `dropping an active builtin into Available preserves unrelated cloze visibility`() {
        val toolbar = EditorFieldToolbar(hiddenButtons = setOf("CLOZE_NEW"))
        val visible = toolbar.orderedItems().filter { it.appliesTo(false) }.map { it.key }
        val order = visible.filterNot { it == "BOLD" } + "BOLD"
        val moved = toolbar.withButtonLayout(order, order.lastIndex, emptyList(), false)
        assertEquals(setOf("CLOZE_NEW", "BOLD"), moved.hiddenButtons)
        assertEquals("BOLD", moved.buttonOrder.last())
        assertEquals(listOf("CLOZE_NEW", "CLOZE_SAME"), moved.buttonOrder.subList(3, 5))
        val restored = moved.withButtonLayout(listOf("BOLD") + order.dropLast(1), order.size, emptyList(), false)
        assertEquals(toolbar.hiddenButtons, restored.hiddenButtons)
        assertEquals(toolbar.orderedItems(), restored.orderedItems())
    }

    @Test
    fun `moving a template between empty groups changes its membership without losing its definition`() {
        val toolbar = EditorFieldToolbar(hiddenButtons = EditorToolbarButton.entries.map { it.name }.toSet())
        val order = toolbar.orderedItems(listOf(ruby)).filter { it.appliesTo(false) }.map { it.key }
        val active = toolbar.withButtonLayout(order, 1, listOf(ruby), false)
        assertEquals(listOf(ruby), active.customButtons)
        assertEquals(toolbar.hiddenButtons, active.hiddenButtons)
        val available = active.withButtonLayout(order, 0, listOf(ruby), false)
        assertTrue(available.customButtons.isEmpty())
        assertEquals(active.buttonOrder, available.buttonOrder)
        assertTrue(available.orderedItems(listOf(ruby)).any { it.key == rubyKey })
    }

    @Test
    fun `reordering across inapplicable commands retains their slots and intervening button order`() {
        val toolbar = EditorFieldToolbar(customButtons = listOf(ruby))
        val visible = toolbar.orderedItems().filter { it.appliesTo(false) }.map { it.key }
        val leading = listOf("BOLD", "ITALIC", "UNDERLINE", "UNDO", rubyKey)
        val order = leading + visible.filterNot { it in leading }
        val moved = toolbar.withButtonLayout(order, order.size, listOf(ruby), false)
        assertEquals(listOf("BOLD", "ITALIC", "UNDERLINE", "UNDO", "CLOZE_NEW", "CLOZE_SAME", rubyKey), moved.buttonOrder.take(7))
        assertEquals(listOf(ruby), moved.customButtons)
        assertFalse(moved.hiddenButtons.contains("CLOZE_NEW"))
        assertEquals(toolbar.orderedItems(), moved.withButtonLayout(visible, visible.size, listOf(ruby), false).orderedItems())
    }
}
