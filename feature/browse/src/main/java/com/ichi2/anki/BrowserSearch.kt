// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.browser.BrowserRowCollection
import com.ichi2.anki.browser.CardOrNoteId
import com.ichi2.anki.browser.search.SearchString
import com.ichi2.anki.browser.search.findCards
import com.ichi2.anki.browser.search.findNotes
import com.ichi2.anki.libanki.SortOrder
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.model.CardsOrNotes.CARDS
import com.ichi2.anki.model.CardsOrNotes.NOTES

suspend fun searchForRows(
    search: SearchString,
    order: SortOrder,
    cardsOrNotes: CardsOrNotes,
): BrowserRowCollection =
    withCol {
        when (cardsOrNotes) {
            CARDS -> findCards(search, order)
            NOTES -> findNotes(search, order)
        }
    }.let { ids ->
        BrowserRowCollection(cardsOrNotes, ids.map { CardOrNoteId(it) }.toMutableList())
    }
