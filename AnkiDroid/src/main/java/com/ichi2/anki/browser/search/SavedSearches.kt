// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser.search

import com.ichi2.anki.CollectionManager.withCol

/** Runs saved-search operations while holding the application's collection lock. */
object SavedSearches {
    suspend fun loadFromConfig(): List<SavedSearch> = withCol { CollectionSavedSearches(config).loadFromConfig() }

    suspend fun saveToConfig(values: List<SavedSearch>) = withCol { CollectionSavedSearches(config).saveToConfig(values) }

    suspend fun byName(name: String): SavedSearch? = withCol { CollectionSavedSearches(config).byName(name) }

    suspend fun add(savedSearch: SavedSearch): Pair<Boolean, List<SavedSearch>> =
        withCol { CollectionSavedSearches(config).add(savedSearch) }

    suspend fun removeByName(searchName: String): Pair<Boolean, List<SavedSearch>> =
        withCol { CollectionSavedSearches(config).removeByName(searchName) }

    suspend fun clear() = withCol { CollectionSavedSearches(config).clear() }
}
