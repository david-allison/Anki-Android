/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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

import anki.config.ConfigKey
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.libanki.CardId
import com.ichi2.libanki.NoteId
import net.ankiweb.rsdroid.Backend

/**
 * An identifier for a row in the Card Browser, which may either be a CardId or a RowId
 *
 * [Backend.browserRowForId]'s functionality depends on:
 * * [ConfigKey.Bool.BROWSER_TABLE_SHOW_NOTES_MODE]
 * * [Backend.setActiveBrowserColumns]
 *
 * @see CardsOrNotes
 */
class BrowserRowCollection(
    val cardsOrNotes: CardsOrNotes,
    val cardOrNoteIdList: MutableList<CardOrNoteId>
) : MutableList<CardOrNoteId> by cardOrNoteIdList {

    fun replaceWith(newList: List<CardOrNoteId>) {
        cardOrNoteIdList.clear()
        cardOrNoteIdList.addAll(newList)
    }

    fun reset() {
        cardOrNoteIdList.clear()
    }

    suspend fun queryNoteIds(): List<NoteId> = when (this.cardsOrNotes) {
        CardsOrNotes.NOTES -> requireNoteIdList()
        CardsOrNotes.CARDS -> CollectionManager.withCol { notesOfCards(cids = requireCardIdList()) }
    }

    suspend fun queryCardIds(): List<CardId> = when (this.cardsOrNotes) {
        // TODO: This is slower than necessary
        CardsOrNotes.NOTES -> requireNoteIdList().flatMap { nid ->
            CollectionManager.withCol {
                cardIdsOfNote(
                    nid = nid
                )
            }
        }
        CardsOrNotes.CARDS -> requireCardIdList()
    }

    fun requireNoteIdList(): List<NoteId> {
        require(cardsOrNotes == CardsOrNotes.NOTES)
        return cardOrNoteIdList.map { it.cardOrNoteId }
    }

    fun requireCardIdList(): List<CardId> {
        require(cardsOrNotes == CardsOrNotes.CARDS)
        return cardOrNoteIdList.map { it.cardOrNoteId }
    }

    suspend fun queryCardIdsAt(position: Int): List<CardId> = when (this.cardsOrNotes) {
        CardsOrNotes.NOTES -> CollectionManager.withCol { cardIdsOfNote(nid = cardOrNoteIdList[position].cardOrNoteId) }
        CardsOrNotes.CARDS -> listOf(cardOrNoteIdList[position].cardOrNoteId)
    }
}
