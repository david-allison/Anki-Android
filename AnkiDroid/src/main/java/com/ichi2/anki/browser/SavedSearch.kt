/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.browser

import androidx.annotation.CheckResult
import com.ichi2.anki.libanki.Collection
import timber.log.Timber

data class SavedSearch(
    val name: String,
    // TODO: validate that this is non-empty
    val terms: String,
)

/**
 * Returns the saved searches in the Collection
 */
@CheckResult
fun Collection.querySavedSearches() = rawSavedSearches().map { SavedSearch(name = it.key, terms = it.value) }

/**
 * Removes the provided search by name
 *
 * @return The updated list of saved searches, or `null` if nothing removed
 */
fun Collection.removeSavedSearchByName(searchName: String): List<SavedSearch>? {
    Timber.d("removing user search")
    val filters = rawSavedSearches().toMutableMap()
    if (filters.remove(searchName) == null) {
        return null
    }
    config.set("savedFilters", filters)
    return filters.map { SavedSearch(name = it.key, terms = it.value) }
}

/**
 * Adds the provided search to the end of the 'saved searches' config
 *
 * @return `null` if no changes were made, otherwise the new list of searches
 */
fun Collection.saveSearch(search: SavedSearch): List<SavedSearch>? {
    Timber.d("saving user search")
    val filters = rawSavedSearches().toMutableMap()
    if (filters[search.name] != null) {
        return null
    }
    filters[search.name] = search.terms
    config.set("savedFilters", filters)
    return filters.map { SavedSearch(name = it.key, terms = it.value) }
}

private fun Collection.rawSavedSearches(): Map<String, String> = config.get("savedFilters") ?: hashMapOf()
