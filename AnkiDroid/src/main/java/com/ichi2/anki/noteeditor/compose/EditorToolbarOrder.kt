// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

/** Built-ins and template references share an order; labels can change without moving a button. */
internal sealed interface EditorToolbarItem {
    val key: String

    data class Builtin(
        val button: EditorToolbarButton,
    ) : EditorToolbarItem {
        override val key: String get() = button.name
    }

    data class Custom(
        val button: EditorCustomToolbarButton,
    ) : EditorToolbarItem {
        override val key: String get() = "custom:${button.id}"
    }
}

/** Older configurations retain their custom-first layout. New buttons append to an explicit order. */
internal fun EditorFieldToolbar.orderedItems(templates: List<EditorCustomToolbarButton> = customButtons): List<EditorToolbarItem> {
    val available =
        (
            (customButtons + templates).distinctBy { it.id }.map { EditorToolbarItem.Custom(it) } +
                EditorToolbarButton.entries.map { EditorToolbarItem.Builtin(it) }
        ).associateBy { it.key }
    return (buttonOrder + available.keys).distinct().mapNotNull(available::get)
}

internal fun EditorToolbarItem.appliesTo(isCloze: Boolean): Boolean = this !is EditorToolbarItem.Builtin || !button.clozeOnly || isCloze

internal fun EditorFieldToolbar.isEnabled(item: EditorToolbarItem): Boolean =
    when (item) {
        is EditorToolbarItem.Builtin -> item.key !in hiddenButtons
        is EditorToolbarItem.Custom -> customButtons.any { it.id == item.button.id }
    }

/** Apply the displayed order and groups, retaining slots for currently inapplicable commands. */
internal fun EditorFieldToolbar.withButtonLayout(
    order: List<String>,
    activeCount: Int,
    templates: List<EditorCustomToolbarButton>,
    isCloze: Boolean,
): EditorFieldToolbar {
    val items = orderedItems(templates)
    val displayed = items.filter { it.appliesTo(isCloze) }
    require(order.size == displayed.size && order.toSet() == displayed.map { it.key }.toSet())
    require(activeCount in 0..order.size)
    val active = order.take(activeCount).toSet()
    val builtins = displayed.filterIsInstance<EditorToolbarItem.Builtin>().map { it.key }.toSet()
    val custom = displayed.filterIsInstance<EditorToolbarItem.Custom>().associateBy { it.key }
    val reordered = order.iterator()
    return copy(
        buttonOrder = items.map { if (it.appliesTo(isCloze)) reordered.next() else it.key },
        hiddenButtons = (hiddenButtons + builtins) - active,
        customButtons = order.take(activeCount).mapNotNull { custom[it]?.button },
    )
}
