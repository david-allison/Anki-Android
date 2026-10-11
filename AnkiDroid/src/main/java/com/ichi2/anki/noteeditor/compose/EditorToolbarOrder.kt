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

/** Swap displayed neighbors, retaining slots belonging to currently inapplicable buttons. */
internal fun EditorFieldToolbar.moveButton(
    key: String,
    offset: Int,
    templates: List<EditorCustomToolbarButton>,
    isCloze: Boolean,
): EditorFieldToolbar {
    require(offset == -1 || offset == 1)
    val items = orderedItems(templates)
    val displayed = items.filter { it.appliesTo(isCloze) }
    val index = displayed.indexOfFirst { it.key == key }
    if (index == -1) return this
    val neighbor = displayed.getOrNull(index + offset) ?: return this
    val order = items.map { it.key }.toMutableList()
    val from = order.indexOf(key)
    val to = order.indexOf(neighbor.key)
    order[from] = neighbor.key
    order[to] = key
    return copy(buttonOrder = order)
}
