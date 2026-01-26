/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.browser.search

import androidx.annotation.CheckResult
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import anki.search.SearchNode
import anki.search.searchNode
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.Flag
import com.ichi2.anki.browser.SearchHistory
import com.ichi2.anki.browser.SearchHistory.SearchHistoryEntry
import com.ichi2.anki.browser.search.CardBrowserSearchViewModel.FilterState
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.DeckNameId
import com.ichi2.anki.libanki.NoteTypeId
import com.ichi2.anki.libanki.NoteTypeNameID
import com.ichi2.anki.libanki.SearchJoiner
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

typealias SubmittedSearch = SearchHistoryEntry

/**
 * A ViewModel for logic relating to creating/selecting a search in the [com.ichi2.anki.CardBrowser]
 *
 * Responsible for:
 *
 * - The search string (unsubmitted)
 * - Saved Searches
 * - History
 * - Advanced Search
 * - Previews
 */
// This is an Activity ViewModel: The SearchView can be directly on the activity
// The sub-fragments (StandardSearchFragment etc...) need to be able to modify/close the
// EditText, but should not be coupled directly to the parent SearchView.
class CardBrowserSearchViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val searchHistoryManager = SearchHistory()

    val advancedSearchFlow =
        savedStateHandle.getMutableStateFlow(STATE_ADVANCED_SEARCH_ENABLED, false)

    private val advancedSearchTextFlow =
        savedStateHandle.getMutableStateFlow(STATE_ADVANCED_SEARCH_TEXT, "")
    private val basicSearchTextFlow =
        savedStateHandle.getMutableStateFlow(STATE_BASIC_SEARCH_TEXT, "")

    val searchTextFlow =
        combine(advancedSearchFlow, basicSearchTextFlow, advancedSearchTextFlow) { displayingAdvancedSearch, basicText, advancedText ->
            if (displayingAdvancedSearch) advancedText else basicText
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = "",
        )

    sealed class SearchHistoryItems {
        data class Loading(
            val input: List<SearchHistoryEntry>,
        ) : SearchHistoryItems() {
            override val entryToSearchString: List<Pair<SearchHistoryEntry, String?>> = input.map { it to null }
        }

        data class Loaded(
            override val entryToSearchString: List<Pair<SearchHistoryEntry, String?>>,
        ) : SearchHistoryItems()

        abstract val entryToSearchString: List<Pair<SearchHistoryEntry, String?>>

        fun isNotEmpty() = entryToSearchString.isNotEmpty()
    }

    val searchHistoryFlow =
        MutableStateFlow<SearchHistoryItems>(
            SearchHistoryItems.Loading(searchHistoryManager.entries),
        )
    val searchHistoryAvailableFlow = searchHistoryFlow.map { it.isNotEmpty() }

    val closeSearchViewFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1, replay = 1)

    val submittedSearchFlow = MutableSharedFlow<SubmittedSearch?>(extraBufferCapacity = 1, replay = 1)

    val savedSearchesFlow = MutableStateFlow<List<SavedSearch>>(emptyList())

    val canManageSavedSearchesFlow = savedSearchesFlow.map { it.isNotEmpty() }

    val userMessageFlow = MutableSharedFlow<UserMessage?>()

    val isScreenOpenFlow = MutableStateFlow(false)

    /** Wraps a [FilterState] so sync updates do not trigger a search */
    // TODO: Make this generic?
    sealed interface FilterUpdate {
        data class User(
            override val state: FilterState,
        ) : FilterUpdate

        data class System(
            override val state: FilterState,
        ) : FilterUpdate

        val state: FilterState
    }

    private val filterStateUpdateFlow = MutableStateFlow<FilterUpdate>(FilterUpdate.System(FilterState()))
    val filterStateFlow =
        filterStateUpdateFlow
            .map { it.state }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = filterStateUpdateFlow.value.state,
            )

    private val updateSearchFlow =
        isScreenOpenFlow
            .flatMapLatest { screenOpen ->
                if (screenOpen) {
                    emptyFlow()
                } else {
                    searchTextFlow.combine(filterStateUpdateFlow) { text, state ->
                        if (state is FilterUpdate.System) {
                            Timber.d("skipped updateSearchFlow: non-user change")
                            return@combine null
                        }
                        buildSubmittedSearch(
                            // various filters add a trailing space
                            query = text.trim(),
                            filters = state.state,
                        )
                    }
                }
            }.filterNotNull()

    private fun updateFilterState(update: (FilterState) -> FilterState) {
        filterStateUpdateFlow.value =
            FilterUpdate.User(
                update(filterStateUpdateFlow.value.state),
            )
    }

    fun setDecksFilter(decks: List<DeckNameId>) {
        Timber.i("set decks filter to [%s]", decks.map { it.id }.joinToString())
        updateFilterState { it.copy(decks = decks) }
    }

    fun setTagsFilter(tags: List<String>) {
        Timber.i("set tags filter to %d tags", tags.size)
        updateFilterState { it.copy(tags = tags) }
    }

    fun setCardStateFilter(value: List<CardState>) {
        Timber.i("updated card state filter to %s", value)
        updateFilterState { it.copy(cardStates = value) }
    }

    init {
        viewModelScope.launch {
            savedSearchesFlow.value = SavedSearches.loadFromConfig()
        }

        updateSearchFlow
            .onEach {
                submitCurrentSearch(it)
            }.launchIn(viewModelScope)

        searchHistoryFlow
            .onEach { history ->
                when (history) {
                    is SearchHistoryItems.Loaded -> return@onEach
                    is SearchHistoryItems.Loading -> {
                        val deckNameMap: List<DeckNameId> = withCol { decks.allNamesAndIds() }
                        val ntidMap: List<NoteTypeNameID> =
                            withCol { notetypes.allNamesAndIds().toList() }

                        val searchStrings =
                            history.input.map {
                                it to withCol { it.buildSearchString(deckNameMap, ntidMap) }
                            }

                        searchHistoryFlow.emit(
                            SearchHistoryItems.Loaded(
                                searchStrings,
                            ),
                        )
                    }
                }
            }.launchIn(viewModelScope)
    }

    /**
     * Toggles between Basic and Advanced search mode
     */
    fun toggleAdvancedSearch() {
        Timber.i("Toggling advanced search to %s", !advancedSearchFlow.value)
        advancedSearchFlow.value = !advancedSearchFlow.value
    }

    /**
     * Appends [searchText] to the current temporary advances search text
     */
    fun appendAdvancedSearch(searchText: String) {
        Timber.d("appending search text '%s'", searchText)
        val currentValue = advancedSearchTextFlow.value
        advancedSearchTextFlow.value +=
            buildString {
                if (currentValue.isNotBlank() && !currentValue.endsWith(" ")) append(' ')
                append(searchText)
                append(' ')
            }
    }

    /**
     * Called on user modification to the non-submitted search text
     */
    fun onSearchTextChanged(searchText: String) {
        Timber.v("onSearchTextChanged '%s'", searchText)

        // move from system back to 'user'
        updateFilterState { it.copy() }

        if (advancedSearchFlow.value) {
            advancedSearchTextFlow.value = searchText
        } else {
            basicSearchTextFlow.value = searchText
        }
    }

    fun submitCurrentSearch(searchToSubmit: SubmittedSearch) =
        viewModelScope.launch {
            Timber.i("submitting search")
            val updatedEntries = searchHistoryManager.addRecent(searchToSubmit)
            searchHistoryFlow.value = SearchHistoryItems.Loading(updatedEntries)
            closeSearchViewFlow.emit(Unit)
            submittedSearchFlow.emit(searchToSubmit)
        }

    fun submitCurrentSearch() {
        isScreenOpenFlow.value = false
    }

    fun selectSearchHistoryEntry(entry: SearchHistoryEntry) =
        viewModelScope.launch {
            Timber.i("selected search history entry")
            onSearchTextChanged(entry.query)
            val allDeckData = withCol { decks.allNamesAndIds() }
            filterStateUpdateFlow.value = FilterUpdate.User(FilterState.from(entry, allDeckData))
            submitCurrentSearch()
        }

    fun addSavedSearch(savedSearch: SavedSearch) =
        viewModelScope.launch {
            Timber.i("Adding saved search")
            val (success, newValues) = SavedSearches.add(savedSearch)
            if (success) {
                savedSearchesFlow.emit(newValues)
                userMessageFlow.emit(UserMessage.SEARCH_SAVED)
            } else {
                userMessageFlow.emit(UserMessage.SAVED_SEARCH_DUPLICATE_ADDED)
            }
        }

    fun deleteSavedSearch(search: SavedSearch) =
        viewModelScope.launch {
            val (success, values) = SavedSearches.removeByName(search.name)
            if (success) {
                savedSearchesFlow.emit(values)
                userMessageFlow.emit(UserMessage.SAVED_SEARCH_DELETED)
            } else {
                userMessageFlow.emit(UserMessage.SAVED_SEARCH_NAME_DOES_NOT_EXIST)
            }
        }

    /**
     * Submits the query in the provided saved search
     */
    fun submitSavedSearch(search: SavedSearch) {
        Timber.i("selected saved search")
        onSearchTextChanged(search.query)
        submitCurrentSearch()
    }

    /**
     * Replaces the current search text with the query in the provided saved search.
     */
    fun applySavedSearch(search: SavedSearch) {
        Timber.i("replacing search with saved search text (not submitting)")
        onSearchTextChanged(search.query + " ")
    }

    /**
     * Clears state when the search screen is closed without saving
     */
    @NeedsTest("reset")
    fun resetSearchState(submittedSearch: SubmittedSearch) =
        viewModelScope.launch {
            Timber.i("clearing temp search state")
            advancedSearchFlow.value = false
            basicSearchTextFlow.value = submittedSearch.query
            advancedSearchTextFlow.value = submittedSearch.query
            val deckData = withCol { decks.allNamesAndIds() }
            filterStateUpdateFlow.value =
                FilterUpdate.System(FilterState.from(submittedSearch, deckData))

            isScreenOpenFlow.value = false

            savedStateHandle.remove<Any>(STATE_ADVANCED_SEARCH_ENABLED)
            savedStateHandle.remove<Any>(STATE_BASIC_SEARCH_TEXT)
            savedStateHandle.remove<Any>(STATE_ADVANCED_SEARCH_TEXT)
        }

    fun syncState(search: SubmittedSearch) =
        viewModelScope.launch {
            Timber.d("syncing search state")
            val deckData = withCol { decks.allNamesAndIds() }
            filterStateUpdateFlow.emit(FilterUpdate.System(FilterState.from(search, deckData)))
            submittedSearchFlow.emit(search)
        }

    enum class UserMessage {
        SEARCH_SAVED,
        SAVED_SEARCH_DUPLICATE_ADDED,
        SAVED_SEARCH_NAME_DOES_NOT_EXIST,
        SAVED_SEARCH_DELETED,
    }

    data class FilterState(
        val decks: List<DeckNameId> = emptyList(),
        val flags: List<Flag> = emptyList(),
        val tags: List<String> = emptyList(),
        val noteTypes: List<NoteTypeId> = emptyList(),
        val cardStates: List<CardState> = emptyList(),
    ) {
        companion object {
            @KotlinCleanup("not used when it should be later on")
            fun from(
                entry: SubmittedSearch,
                allDeckData: List<DeckNameId>,
            ) = FilterState(
                decks = entry.deckIds.map { did -> allDeckData.first { it.id == did } },
                flags = entry.flags,
                tags = entry.tags,
                noteTypes = entry.noteTypes,
                cardStates = entry.cardStates,
            )
        }
    }

    companion object {
        private const val STATE_ADVANCED_SEARCH_ENABLED = "advancedSearch"
        private const val STATE_BASIC_SEARCH_TEXT = "basicSearchText"
        private const val STATE_ADVANCED_SEARCH_TEXT = "advancedSearchText"
    }
}

private fun buildSubmittedSearch(
    query: String,
    filters: FilterState,
) = SubmittedSearch(
    query = query,
    deckIds = filters.decks.map { it.id },
    flags = filters.flags,
    tags = filters.tags,
    noteTypes = filters.noteTypes,
    cardStates = filters.cardStates,
)

@CheckResult
context(col: Collection)
fun SubmittedSearch.buildSearchString(): String? {
    val deckNameMap: List<DeckNameId> = if (this.deckIds.any()) col.decks.allNamesAndIds() else emptyList()
    val ntidMap: List<NoteTypeNameID> = if (this.noteTypes.any()) col.notetypes.allNamesAndIds().toList() else emptyList()

    return this.buildSearchString(deckNameMap, ntidMap)
}

@CheckResult
context(col: Collection)
fun SubmittedSearch.buildSearchString(
    deckNameMap: List<DeckNameId>,
    ntidMap: List<NoteTypeNameID>,
): String? {
    val search = this

    val nodeList =
        try {
            buildList {
                fun <T> List<T>.toGroupNode(transform: (T) -> SearchNode): SearchNode? {
                    if (this.isEmpty()) return null
                    val searchNodes = this.map(transform)
                    return col.groupSearches(
                        searchNodes,
                        SearchJoiner.OR,
                    )
                }

                fun didToName(id: DeckId) = deckNameMap.firstOrNull { it.id == id }?.name

                fun ntidToName(id: NoteTypeId) = ntidMap.firstOrNull { it.id == id }?.name

                // a blank search should be provided if there are no filters
                if (!hasFiltersSet || search.query.isNotBlank()) {
                    add(searchNode { parsableText = search.query.trim() })
                }

                add(
                    search.deckIds.toGroupNode { did ->
                        searchNode { deck = didToName(did) ?: throw IllegalStateException() }
                    },
                )
                add(search.flags.toGroupNode { it.toSearchNode() })
                add(search.tags.toGroupNode { t -> searchNode { tag = t } })
                add(
                    search.noteTypes.toGroupNode { ntid ->
                        searchNode { note = ntidToName(ntid) ?: throw IllegalStateException() }
                    },
                )
                add(search.cardStates.toGroupNode { it.toSearchNode() })
            }.filterNotNull()
        } catch (_: IllegalStateException) {
            // TODO: using exception as control flow
            return null
        }

    return col.buildSearchString(nodeList, SearchJoiner.AND)
}
