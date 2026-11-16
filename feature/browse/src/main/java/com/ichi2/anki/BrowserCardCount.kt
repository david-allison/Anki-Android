// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import com.ichi2.anki.browser.CardBrowserViewModel.SearchState
import com.ichi2.anki.model.CardsOrNotes.CARDS

fun SearchState.Completed.formatCardCount(resources: android.content.res.Resources): String =
    resources.getQuantityString(
        if (cardsOrNotes == CARDS) CommonPlurals.card_browser_subtitle else CommonPlurals.card_browser_subtitle_notes_mode,
        rowCount,
        rowCount,
    )
