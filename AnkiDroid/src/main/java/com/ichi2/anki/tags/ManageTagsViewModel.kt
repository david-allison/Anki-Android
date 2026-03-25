/*
 * Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.tags

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.Tags
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.tags.ManageTagsEvent.DisplayMessage
import com.ichi2.anki.tags.ManageTagsState.Error
import com.ichi2.anki.tags.UserMessage.ClearedUnusedTags
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import anki.tags.TagTreeNode as BackendTagTreeNode

/**
 * ViewModel for the Manage Tags screen.
 *
 * Handles display of hierarchical tags with expand/collapse and a search filter.
 *
 * Operations may be applied using either a multiselect mode or to a single tag.
 *
 * This must handle a huge amount of tags, AnKing v11 contains ~17k tags.
 *
 * @see Tags for backend functions.
 * @see TagListItem for display.
 * @see ManageTagsState for UI state.
 * @see ManageTagsEvent for one-shot events.
 */
class ManageTagsViewModel : ViewModel() {
    val state: StateFlow<ManageTagsState>
        field = MutableStateFlow<ManageTagsState>(ManageTagsState.Loading)

    private val _events = Channel<ManageTagsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** Cached backend tree for re-flattening on collapse/filter changes without a backend call */
    private var backendTree: BackendTagTreeNode? = null

    init {
        refreshTags()
    }

    /** Reloads the tag tree from the backend. Preserves the current search query if any */
    fun refreshTags() {
        val previousLoaded = state.value as? ManageTagsState.Loaded
        state.value = ManageTagsState.Loading
        viewModelScope.launch {
            try {
                loadTags(previousLoaded?.searchQuery ?: "")
            } catch (e: Exception) {
                Timber.w(e, "Failed to load tags")
                state.value = Error(e)
            }
        }
    }

    private suspend fun loadTags(searchQuery: String) {
        Timber.i("Loading tags from collection")
        val tree = withCol { tags.tree() }
        backendTree = tree
        state.value =
            ManageTagsState.Loaded(
                visibleNodes = flattenTree(tree, searchQuery),
                searchQuery = searchQuery,
            )
    }

    // TODO: decide behavior when filtering while tags are selected:
    //  - clear selection on filter change?
    //  - preserve selection but hide non-matching selected tags?
    //  - disable filtering while in multi-select mode?

    /** Filters visible tags by [query]. Ancestors of matches are shown to preserve hierarchy */
    fun filter(query: String) {
        val tree = backendTree ?: return
        updateState { loaded ->
            loaded.copy(
                searchQuery = query,
                visibleNodes = flattenTree(tree, query),
            )
        }
    }

    /**
     * Expands or collapses [tag] in the tree. Persists the state to the backend.
     *
     * PERF: ~55ms to process 17k tags.
     *
     * @see Tags.setCollapsed
     */
    fun toggleCollapsed(tag: TagName) =
        viewModelScope.launch {
            try {
                val loaded = state.value as? ManageTagsState.Loaded ?: return@launch
                val node = loaded.visibleNodes.firstOrNull { it.fullTag == tag } ?: return@launch
                val newCollapsed = !node.collapsed
                withCol { tags.setCollapsed(tag, newCollapsed) }
                // re-fetch tree to get updated collapsed state
                val tree = withCol { tags.tree() }
                backendTree = tree
                updateState { it.copy(visibleNodes = flattenTree(tree, it.searchQuery)) }
            } catch (e: Exception) {
                Timber.w(e, "Failed to toggle collapsed state")
            }
        }

    /**
     * Toggles selection of [tag] and all its descendants.
     *
     * - NONE → SELECTED: selects the tag and all descendants
     * - SELECTED → NONE: deselects the tag, its descendants, and all ancestors
     *   (ancestors become indeterminate or unselected)
     * - INDETERMINATE → SELECTED: selects the tag and all descendants,
     *   filling in any unselected children
     */
    fun toggleSelection(tag: TagName) {
        val tree = backendTree ?: return
        val subtree = getSubtreeTagNames(tree, tag)
        updateState { loaded ->
            val isSelected = tag in loaded.selectedTags
            val newSelected =
                if (isSelected) {
                    val ancestors = tag.ancestors
                    loaded.selectedTags - subtree - ancestors
                } else {
                    loaded.selectedTags + subtree
                }
            loaded.copy(
                selectedTags = newSelected,
                visibleNodes = loaded.visibleNodes.withSelectionStates(newSelected),
            )
        }
    }

    /** Deselects all tags, exiting multi-select mode */
    fun clearSelection() =
        updateState {
            it.copy(
                selectedTags = emptySet(),
                visibleNodes = it.visibleNodes.withSelectionStates(emptySet()),
            )
        }

    /**
     * Deletes all currently selected tags and their children. No-ops if selection is empty.
     * @see Tags.remove
     */
    fun deleteSelected() {
        val loaded = state.value as? ManageTagsState.Loaded ?: return
        val selected = loaded.selectedTags
        if (selected.isEmpty()) return
        launchUndoableTagOperation { tags.remove(selected) }
    }

    /**
     * Deletes [tag] and its children from all notes.
     * @see Tags.remove
     */
    fun deleteTag(tag: TagName) = launchUndoableTagOperation { tags.remove(tag) }

    /**
     * Renames [oldName] to [newName], updating all notes and child tags.
     * @see Tags.rename
     */
    fun renameTag(
        oldName: TagName,
        newName: TagName,
    ) = launchUndoableTagOperation { tags.rename(oldName, newName) }

    /**
     * Removes tags not present on any note. Emits a [ClearedUnusedTags] event with the count.
     * @see Tags.clearUnusedTags
     */
    fun clearUnusedTags() =
        launchTagOperation {
            val result = undoableOp { tags.clearUnusedTags() }
            Timber.i("Deleted %d unused tags", result.count)
            _events.send(DisplayMessage(ClearedUnusedTags(result.count)))
        }

    /**
     * Wraps a [Collection]-scoped block in [undoableOp] and delegates to [launchTagOperation]
     *
     * @see undoableOp warning on implementation: [block] must return an `OpChanges`
     */
    private fun launchUndoableTagOperation(block: Collection.() -> Any): Job = launchTagOperation { undoableOp(block = block) }

    /**
     * Sets [ManageTagsState.Loading], runs [block], then reloads.
     * On failure, transitions to [Error]
     */
    private fun launchTagOperation(block: suspend () -> Unit): Job {
        val previousLoaded = state.value as? ManageTagsState.Loaded
        state.value = ManageTagsState.Loading
        return viewModelScope.launch {
            try {
                block()
                loadTags(previousLoaded?.searchQuery ?: "")
            } catch (e: Exception) {
                state.value = Error(e)
            }
        }
    }

    /** Applies [transform] only if the current state is [ManageTagsState.Loaded]; no-ops otherwise */
    private inline fun updateState(transform: (ManageTagsState.Loaded) -> ManageTagsState.Loaded) {
        state.update { current ->
            when (current) {
                is ManageTagsState.Loaded -> transform(current)
                else -> {
                    Timber.w("updateState called while in %s; ignoring", current::class.simpleName)
                    current
                }
            }
        }
    }

    companion object {
        /**
         * Converts the backend's recursive [BackendTagTreeNode] into a flat list of [TagListItem]s,
         * respecting collapsed state and search filtering.
         * Ancestors of search matches are included to preserve tree structure.
         */
        @VisibleForTesting
        fun flattenTree(
            root: BackendTagTreeNode,
            searchQuery: String,
        ): List<TagListItem> {
            val result = mutableListOf<TagListItem>()
            val isSearching = searchQuery.isNotBlank()

            fun traverse(
                node: BackendTagTreeNode,
                parentFullTag: String,
            ) {
                for (child in node.childrenList) {
                    val fullTag = if (parentFullTag.isEmpty()) child.name else "$parentFullTag::${child.name}"
                    val hasChildren = child.childrenList.isNotEmpty()
                    val matchesSearch = !isSearching || fullTag.contains(searchQuery, ignoreCase = true)
                    val hasDescendantMatch = isSearching && hasDescendantMatching(child, fullTag, searchQuery)

                    if (matchesSearch || hasDescendantMatch) {
                        result.add(
                            TagListItem(
                                fullTag = TagName(fullTag),
                                displayName = child.name,
                                level = child.level - 1,
                                hasChildren = hasChildren,
                                collapsed = if (isSearching) false else child.collapsed,
                            ),
                        )
                    }

                    // recurse into children if expanded (or searching with descendant matches)
                    if (hasChildren && (!child.collapsed || isSearching)) {
                        traverse(child, fullTag)
                    }
                }
            }

            traverse(root, "")
            return result
        }

        /**
         * Returns a copy of the nodes with [SelectionState] computed for each node.
         *
         * A node is [SELECTED][SelectionState.SELECTED] if it's in [selectedTags].
         * A node is [INDETERMINATE][SelectionState.INDETERMINATE] if it's not selected
         * but has at least one descendant in [selectedTags].
         */
        fun List<TagListItem>.withSelectionStates(selectedTags: Set<TagName>): List<TagListItem> {
            if (selectedTags.isEmpty()) {
                return map { it.copy(selectionState = SelectionState.NONE) }
            }
            return map { node ->
                val state =
                    when {
                        node.fullTag in selectedTags -> SelectionState.SELECTED
                        node.hasChildren && node.fullTag.hasDescendantIn(selectedTags) -> SelectionState.INDETERMINATE
                        else -> SelectionState.NONE
                    }
                node.copy(selectionState = state)
            }
        }

        /**
         * Returns [tag] plus all its descendants' full tag names from [root].
         *
         * O(n)
         */
        private fun getSubtreeTagNames(
            root: BackendTagTreeNode,
            tag: TagName,
        ): Set<TagName> {
            val result = mutableSetOf<TagName>()

            fun collectDescendants(
                node: BackendTagTreeNode,
                parentFullTag: String,
            ) {
                for (child in node.childrenList) {
                    val fullTag = if (parentFullTag.isEmpty()) child.name else "$parentFullTag::${child.name}"
                    result.add(TagName(fullTag))
                    collectDescendants(child, fullTag)
                }
            }

            fun findAndCollect(
                node: BackendTagTreeNode,
                parentFullTag: String,
            ): Boolean {
                for (child in node.childrenList) {
                    val fullTag = if (parentFullTag.isEmpty()) child.name else "$parentFullTag::${child.name}"
                    if (TagName(fullTag) == tag) {
                        result.add(tag)
                        collectDescendants(child, fullTag)
                        return true
                    }
                    if (findAndCollect(child, fullTag)) return true
                }
                return false
            }

            findAndCollect(root, "")
            return result
        }

        private fun hasDescendantMatching(
            node: BackendTagTreeNode,
            parentFullTag: String,
            searchQuery: String,
        ): Boolean {
            for (child in node.childrenList) {
                val fullTag = "$parentFullTag::${child.name}"
                if (fullTag.contains(searchQuery, ignoreCase = true)) return true
                if (hasDescendantMatching(child, fullTag, searchQuery)) return true
            }
            return false
        }
    }
}

/**
 * A flattened representation of a tag for display in a RecyclerView.
 *
 * @param fullTag full hierarchical tag path, e.g. "science::biology"
 * @param displayName leaf name only, e.g. "biology"
 * @param level tree depth (0 = top-level)
 * @param selectionState tri-state: NONE, SELECTED, or INDETERMINATE
 */
data class TagListItem(
    val fullTag: TagName,
    val displayName: String,
    val level: Int,
    val hasChildren: Boolean,
    val collapsed: Boolean,
    val selectionState: SelectionState = SelectionState.NONE,
)

enum class SelectionState {
    /** Tag is not selected */
    NONE,

    /** Tag and all its descendants are selected */
    SELECTED,

    /** Some (but not all) descendants are selected */
    INDETERMINATE,
}

sealed class ManageTagsState {
    data object Loading : ManageTagsState()

    data class Loaded(
        val visibleNodes: List<TagListItem>,
        val selectedTags: Set<TagName> = emptySet(),
        val searchQuery: String = "",
    ) : ManageTagsState() {
        val isInMultiSelectMode: Boolean get() = selectedTags.isNotEmpty()
    }

    data class Error(
        val error: Throwable,
    ) : ManageTagsState()
}

sealed interface ManageTagsEvent {
    data class DisplayMessage(
        val message: UserMessage,
    ) : ManageTagsEvent
}

sealed interface UserMessage {
    data class ClearedUnusedTags(
        val count: Int,
    ) : UserMessage
}
