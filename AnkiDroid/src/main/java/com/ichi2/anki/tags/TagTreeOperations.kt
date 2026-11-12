// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import anki.collection.OpChangesWithCount
import anki.collection.copy
import anki.collection.opChangesWithCount
import anki.tags.TagTreeNode
import com.ichi2.anki.libanki.Collection

/** The backend can only rename a subtree referenced by at least one note. */
internal fun Collection.canRenameTagTree(tag: TagName): Boolean = findNotes(tag.value.literalSubtreeSearch()).isNotEmpty()

/**
 * Renames tags on notes, preserving expansion and repairing duplicates when subtrees merge.
 *
 * Works around [Anki issue 5857](https://github.com/ankitects/anki/issues/5857).
 *
 * Follows Anki's backend behavior: an entirely unused subtree is left unchanged. When
 * renaming a subtree used by notes, unused cached descendants are removed, not renamed.
 * See https://github.com/ankitects/anki/blob/main/rslib/src/tags/rename.rs.
 */
internal fun Collection.renameTagTree(
    oldName: TagName,
    newName: TagName,
): OpChangesWithCount {
    // TODO: Preserve expansion in the backend rename transaction and remove this workaround.
    val expansion = renamedTagState(tags.tree().expansionState(), oldName, newName)
    val affectedNotes = findNotes(oldName.value.literalSubtreeSearch())
    val result = tags.rename(oldName, newName)
    if (result.count == 0) return result

    // Renaming into an existing tag can leave duplicate tags on a note. Use the backend's
    // tag identity to deduplicate: its pinned unicase 2.6.0 has a folding bug that equates
    // "ﬃ" with "iff", but keeps "ffi" distinct. Standard Unicode folding disagrees, so
    // client-side cleanup can remove valid tags or miss duplicates.
    // The rename already marked these notes modified; no card generation is needed.
    val undoStep = undoStatus().lastStep
    afterNoteUpdates(affectedNotes, markModified = false, generateCards = false)
    // Only restore surviving paths, using their canonical spelling. Unused descendants stay removed.
    val currentExpansion = tags.tree().expansionState()
    val canonical = TagNameLookup(currentExpansion.keys + expansion.keys).canonicalNames(this)
    val restored = expansion.mapKeys { canonical.getValue(it.key) }
    for ((tag, expanded) in currentExpansion) {
        if (!expanded && restored[tag] == true) tags.setCollapsed(tag, false)
    }
    return result.copy { changes = mergeUndoEntries(undoStep) }
}

/** Move tags on notes, merging overlapping subtrees with the same semantics as [renameTagTree]. */
internal fun Collection.moveTagTree(
    tag: TagName,
    newParent: TagName?,
): OpChangesWithCount {
    require(newParent == null || !tagIsInSubtree(newParent, tag)) { "A tag cannot be moved under itself" }
    val destination = tag.withParent(newParent)
    if (sameTag(destination, tag)) return opChangesWithCount { }
    // Moving one subtree is a prefix rename. The backend reparent operation also rejects
    // valid sibling prefixes (e.g. parent::a beneath parent::ab).
    return renameTagTree(tag, destination)
}

/** Renames state keys in a subtree. Existing destinations keep their state when subtrees merge. */
internal fun <T> Collection.renamedTagState(
    state: Map<TagName, T>,
    oldName: TagName,
    newName: TagName,
): Map<TagName, T> {
    val depth = oldName.value.split("::").size
    val prefixes = TagNameLookup(state.keys.mapNotNull { it.prefix(depth) }).matching(this, oldName).toSet()
    val (renamed, unchanged) = state.entries.partition { it.key.prefix(depth) in prefixes }
    val rewritten =
        renamed.associate { (tag, value) ->
            val suffix = tag.value.split("::").drop(depth)
            requireNotNull(TagName.build((listOf(newName.value) + suffix).joinToString("::"))) to value
        }
    val canonical = TagNameLookup(unchanged.map { it.key } + rewritten.keys).canonicalNames(this)
    return rewritten.mapKeys { canonical.getValue(it.key) } + unchanged.associate { it.toPair() }
}

/** Includes implicit parents, which can be merge destinations without a tag record of their own. */
private fun TagTreeNode.expansionState(): Map<TagName, Boolean> =
    buildMap {
        fun visit(
            node: TagTreeNode,
            parent: String,
        ) {
            for (child in node.childrenList) {
                val name = if (parent.isEmpty()) child.name else "$parent::${child.name}"
                put(requireNotNull(TagName.build(name)), !child.collapsed)
                visit(child, name)
            }
        }
        visit(this@expansionState, "")
    }

/** A literal regex avoids tag-search wildcards and reserved names such as "none". */
private fun String.literalSubtreeSearch(): String {
    val escaped =
        buildString {
            for (char in this@literalSubtreeSearch) {
                if (char in "\\.^$|?*+()[]{}") append('\\')
                append(char)
            }
        }.replace("\"", "\\\"")
    return "\"tag:re:^$escaped(?:::|$)\""
}
