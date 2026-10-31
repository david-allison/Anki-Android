// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.model

import com.ichi2.anki.common.utils.ext.cardsOrNotes
import com.ichi2.anki.libanki.Collection

/**
 * Config: Whether the `CardBrowser` is in "Cards" or "Notes" mode
 *
 * @see cardsOrNotes
 */
enum class CardsOrNotes {
    CARDS,
    NOTES,
    ;

    fun saveToCollection(col: Collection) {
        col.config.cardsOrNotes = this
    }

    companion object {
        fun fromCollection(col: Collection): CardsOrNotes = col.config.cardsOrNotes
    }
}
