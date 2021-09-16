/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.servicelayer

import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.libanki.*
import com.ichi2.libanki.UndoAction.revertCardToProvidedState
import timber.log.Timber

typealias NextCardResult<T> = Result<SchedulerService.NextCard<T>>

class SchedulerService {
    fun sampleExecution() {
        val card: Card = null!!
        BuryCard(card)
            .before { }
            .after { nextCardResult -> Timber.w("%s", nextCardResult) }
            .onProgressUpdate { progress -> Timber.w("%s", progress) }
            .onCancelled { }
            .execute()
    }

    class BuryCard(val card: Card) : AnkiMethod<NextCardResult<cid>>() {
        override fun execute(): NextCardResult<cid> {
            return computeThenGetNextCardInTransaction {
                // collect undo information
                col.markUndo(revertCardToProvidedState(R.string.menu_bury_card, card))
                // then bury
                col.sched.buryCards(longArrayOf(card.id))
                return@computeThenGetNextCardInTransaction card.id
            }
        }
    }

    /**
     * Handles the result from a method obtaining the next card
     * Either:
     *   HasNextCard + method result
     *   NoMoreCards + method result
     */
    abstract class NextCard<T>(val result: T) {
        class HasNextCard<T>(result: T, val card: Card) : NextCard<T>(result)
        class NoMoreCards<T>(result: T) : NextCard<T>(result)

        companion object {
            fun <T> fromCard(card: Card?, result: T): NextCard<T> =
                if (card != null) HasNextCard(result, card) else NoMoreCards(result)
        }
    }

    companion object {
        fun <T> AnkiMethod<NextCardResult<T>>.computeThenGetNextCardInTransaction(task: (com.ichi2.libanki.Collection) -> T): NextCardResult<T> {
            try {
                return col.db.executeInTransaction2 {
                    col.sched.deferReset()
                    val taskResult = task(col)
                    // With sHadCardQueue set, getCard() resets the scheduler prior to getting the next card
                    val maybeNextCard: Card? = col.sched.getCard()

                    val success = NextCard.fromCard(maybeNextCard, taskResult)
                    return@executeInTransaction2 Result.success(success)
                }
            } catch (e: RuntimeException) {
                Timber.e(e, "doInBackgroundDismissNote - RuntimeException on dismissing note, dismiss type %s", this.javaClass)
                AnkiDroidApp.sendExceptionReport(e, "doInBackgroundDismissNote")
                return Result.failure(e)
            }
        }
    }
}
