// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.libanki

import anki.scheduler.CardAnswer.Rating
import com.ichi2.anki.libanki.sched.SetDueDateDays
import com.ichi2.anki.libanki.testutils.InMemoryAnkiTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FsrsIntegrationTest : InMemoryAnkiTest() {
    @Test
    fun `moving a reviewed card recomputes memory without rescheduling and is undoable`() {
        col.config.set("fsrs", true)
        val target = col.decks.id("target")
        addBasicNote("moved", "back")
        val card = col.sched.card!!
        col.sched.answerCard(card, Rating.EASY)
        val before = col.getCard(card.id)
        assertNotNull(before.memoryState)
        val untouched = addBasicNote("untouched", "back").firstCard(col).toBackendCard()

        col.setDeck(listOf(card.id), target)

        val moved = col.getCard(card.id)
        assertEquals(target, moved.did)
        assertNotNull(moved.memoryState)
        assertEquals(before.due, moved.due)
        assertEquals(before.ivl, moved.ivl)
        assertEquals(before.reps, moved.reps)
        assertEquals(untouched, col.getCard(untouched.id).toBackendCard())

        col.undo()
        assertEquals(before.toBackendCard(), col.getCard(card.id).toBackendCard())
    }

    @Test
    fun `repeated set due date does not invent FSRS review history for a new card`() {
        col.config.set("fsrs", true)
        val card = addBasicNote().firstCard(col)
        for (days in listOf(5, 10)) {
            col.sched.setDueDate(listOf(card.id), SetDueDateDays(days.toString()))
            val updated = col.getCard(card.id)
            assertEquals(col.sched.today + days, updated.due)
            assertEquals(0, updated.ivl)
            assertNull(updated.memoryState)
            assertEquals(0, updated.reps)
        }
    }

    @Test
    fun `set due date retains SM2 interval behavior without FSRS`() {
        col.config.set("fsrs", false)
        val card = addBasicNote().firstCard(col)
        col.sched.setDueDate(listOf(card.id), SetDueDateDays("5"))

        val updated = col.getCard(card.id)
        assertEquals(5, updated.ivl)
        assertEquals(CardType.Rev, updated.type)
        assertNull(updated.memoryState)
    }
}
