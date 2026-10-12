// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

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
    fun `moving a disabled template across Anki buttons does not enable it`() {
        val toolbar =
            EditorFieldToolbar()
                .moveButtonTo(rubyKey, "BOLD", listOf(ruby), false)
                .moveButtonTo("ITALIC", rubyKey, listOf(ruby), false)
        assertEquals(listOf("BOLD", "ITALIC", rubyKey, "UNDERLINE"), toolbar.buttonOrder.take(4))
        assertEquals(emptyList(), toolbar.customButtons)
        assertEquals(listOf("BOLD", "ITALIC", "UNDERLINE"), toolbar.orderedItems().take(3).map { it.key })
        assertEquals(
            listOf("BOLD", "ITALIC", rubyKey),
            toolbar
                .copy(customButtons = listOf(ruby))
                .orderedItems()
                .take(3)
                .map { it.key },
        )
    }

    @Test
    fun `moving across inapplicable cloze commands retains their slots`() {
        val moved = EditorFieldToolbar().moveButtonTo("UNDERLINE", "UNDO", emptyList(), false)
        assertEquals(listOf("BOLD", "ITALIC", "UNDO", "CLOZE_NEW", "CLOZE_SAME", "UNDERLINE"), moved.buttonOrder.take(6))
        val cloze = EditorFieldToolbar().moveButtonTo("UNDERLINE", "CLOZE_NEW", emptyList(), true)
        assertEquals(listOf("BOLD", "ITALIC", "CLOZE_NEW", "UNDERLINE", "CLOZE_SAME", "UNDO"), cloze.buttonOrder.take(6))
    }

    @Test
    fun `a drag across multiple rows shifts intervening buttons in order`() {
        val toolbar = EditorFieldToolbar(customButtons = listOf(ruby))
        val moved = toolbar.moveButtonTo(rubyKey, "UNDO", listOf(ruby), false)
        assertEquals(listOf("BOLD", "ITALIC", "UNDERLINE", "UNDO", "CLOZE_NEW", "CLOZE_SAME", rubyKey), moved.buttonOrder.take(7))
        assertEquals(listOf(ruby), moved.customButtons)
        assertEquals(toolbar.orderedItems(), moved.moveButtonTo(rubyKey, "BOLD", listOf(ruby), false).orderedItems())
    }

    @Test
    fun `self moves and absent buttons leave the configuration unchanged`() {
        val toolbar = EditorFieldToolbar()
        assertSame(toolbar, toolbar.moveButtonTo("BOLD", "BOLD", emptyList(), false))
        assertSame(toolbar, toolbar.moveButtonTo("RECORD_AUDIO", "missing", emptyList(), false))
        assertSame(toolbar, toolbar.moveButtonTo("CLOZE_NEW", "BOLD", emptyList(), false))
    }
}
