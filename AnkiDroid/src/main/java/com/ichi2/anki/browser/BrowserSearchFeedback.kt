// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

/** Why the browser is running a search. */
enum class BrowserSearchTrigger {
    /** Refreshes without count feedback, including operations which provide their own message. */
    AUTOMATIC,

    /** The user submitted a query, selected a search filter, or launched an external search. */
    USER_SEARCH,

    /** An edit or a cards/notes mode change requests the refreshed count. */
    USER_REFRESH,
}

/** The feedback to display after a browser search completes. */
internal enum class BrowserSearchFeedback(
    val offersSearchAllDecks: Boolean = false,
) {
    NONE,
    COUNT,
    COUNT_WITH_SEARCH_ALL_DECKS(offersSearchAllDecks = true),
    NO_CARDS_IN_SELECTED_DECK(offersSearchAllDecks = true),
}

internal fun browserSearchFeedback(
    trigger: BrowserSearchTrigger,
    rowCount: Int,
    allDecksSelected: Boolean,
    isHeaderCountVisible: Boolean,
): BrowserSearchFeedback =
    // The search facts come from the completed search; header visibility is checked when displaying it.
    when (trigger) {
        // Ordinary opening, deck changes and sorting stay quiet (Issue 21242).
        // Operations with their own feedback use an automatic refresh.
        BrowserSearchTrigger.AUTOMATIC -> BrowserSearchFeedback.NONE
        // User actions without their own message report the hidden count, without an action (Issue 22384).
        BrowserSearchTrigger.USER_REFRESH ->
            if (isHeaderCountVisible) BrowserSearchFeedback.NONE else BrowserSearchFeedback.COUNT
        BrowserSearchTrigger.USER_SEARCH ->
            when {
                // A search within a deck also offers "Search all decks", even with a visible header (Issue 5159).
                !allDecksSelected && rowCount == 0 -> BrowserSearchFeedback.NO_CARDS_IN_SELECTED_DECK
                !allDecksSelected -> BrowserSearchFeedback.COUNT_WITH_SEARCH_ALL_DECKS
                // Submitted searches show the count when the header hides it (Issue 3592).
                isHeaderCountVisible -> BrowserSearchFeedback.NONE
                else -> BrowserSearchFeedback.COUNT
            }
    }
