// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import android.content.Context
import android.content.Intent
import com.ichi2.anki.common.destinations.BrowserDestination

/** Builds an explicit intent for the application host without depending on the app module. */
fun BrowserDestination.toIntent(context: Context): Intent =
    when (this) {
        is BrowserDestination.Open -> Intent().setClassName(context, CARD_BROWSER_ACTIVITY)
        is BrowserDestination.ToDeck ->
            Intent().setClassName(context, CARD_BROWSER_ACTIVITY).apply {
                putExtra(CardBrowserViewModel.EXTRA_DECK_ID, deckId)
            }
        is BrowserDestination.ScrollToCard ->
            Intent().setClassName(context, CARD_BROWSER_ACTIVITY).apply {
                putExtra(CardBrowserViewModel.EXTRA_DECK_ID, deckId)
                putExtra(CardBrowserViewModel.EXTRA_CARD_ID_KEY, cardId)
            }
        is BrowserDestination.Search ->
            Intent().setClassName(context, CARD_BROWSER_ACTIVITY).apply {
                putExtra(CardBrowserViewModel.EXTRA_SEARCH_QUERY, query)
                putExtra(CardBrowserViewModel.EXTRA_ALL_DECKS, allDecks)
            }
    }

private const val CARD_BROWSER_ACTIVITY = "com.ichi2.anki.CardBrowser"
