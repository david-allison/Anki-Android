// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewer

import android.content.Context
import android.os.SystemClock
import android.view.View
import android.widget.Chronometer
import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.Collection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.ichi2.anki.common.android.R as CommonR

/**
 * Responsible for pause/resume of the card timer and the UI element displaying the amount of time to answer a card
 *
 * This is the time to answer the question, and to view the answer
 *
 * Handled by the "Show answer timer" and "Maximal answer time" in the deck options.
 *
 * This is not responsible for the initial call to [Card.startTimer], but does handle this on pause/resume
 *
 * @see [Card.timeTaken] - used by the scheduler
 */
class AnswerTimer internal constructor(
    private val display: Display,
    private val elapsedRealtime: () -> Long,
) {
    constructor(cardTimer: Chronometer) : this(ChronometerDisplay(cardTimer), SystemClock::elapsedRealtime)

    /** The timer controller only needs a display and a monotonic clock. */
    internal interface Display {
        var visibility: Int
        var base: Long

        fun start()

        fun stop()

        fun resetColor()

        fun highlightLimit()

        fun setOnTickListener(listener: () -> Unit)
    }

    @VisibleForTesting
    var limit: Int = 0
        private set

    private lateinit var currentCard: Card

    var showTimer: Boolean = false
        private set

    /**
     * Changes the timer visibility based on [Card.shouldShowTimer],
     * resets the timer to an initial state and starts it
     *
     * This may also change the limit, based on [Card.timeLimit]
     */
    @MainThread // resetTimerUI
    fun setupForCard(
        col: Collection,
        newCard: Card,
    ) {
        currentCard = newCard
        showTimer = newCard.shouldShowTimer(col)
        if (showTimer && display.visibility == View.INVISIBLE) {
            display.visibility = View.VISIBLE
        } else if (!showTimer && display.visibility != View.INVISIBLE) {
            display.visibility = View.INVISIBLE
        }

        // Optimisation to remove the number of timers. Speedup at call site is negligible (~500 micros)
        if (!showTimer) {
            display.stop()
        } else {
            resetTimerUI(col, newCard)
        }
    }

    @MainThread // display.base
    private fun resetTimerUI(col: Collection, newCard: Card) {
        // Set normal timer color
        display.resetColor()

        display.base = elapsedRealTime
        display.start()

        // Stop and highlight the timer if it reaches the time limit.
        limit = newCard.timeLimit(col)
        display.setOnTickListener {
            val elapsed: Long = elapsedRealTime - display.base
            if (elapsed >= limit) {
                display.highlightLimit()
                display.stop()
            }
        }
    }

    fun pause() {
        if (!this::currentCard.isInitialized) {
            return
        }

        // We stop the UI timer so it doesn't trigger the tick listener while paused. Letting
        // it run would trigger the time limit condition (red, stopped timer) in the background.
        display.stop()
        currentCard.stopTimer()
    }

    suspend fun resume() {
        if (!this::currentCard.isInitialized) {
            return
        }
        // Resume the card timer first. It internally accounts for the time gap between
        // suspend and resume.
        currentCard.resumeTimer()

        if (!showTimer) {
            return
        }
        // Then update and resume the UI timer. Set the base time as if the timer had started
        // timeTaken() seconds ago.
        setBase(elapsedRealTime - withCol { currentCard.timeTaken(this@withCol) })

        // Don't start the timer if we have already reached the time limit or it will tick over
        if (elapsedRealTime - display.base < withCol { currentCard.timeLimit(this@withCol) }) {
            display.start()
        }
    }

    fun setVisibility(visibility: Int) {
        if (!showTimer) {
            return
        }

        display.visibility = visibility
    }

    /** Milliseconds since boot */
    private val elapsedRealTime
        get() = elapsedRealtime()

    /** @see Chronometer.setBase */
    private suspend fun setBase(base: Long) = withContext(Dispatchers.Main) { display.base = base }
}

/** Android rendering stays at the boundary; card timer state can run on the JVM. */
private class ChronometerDisplay(
    private val chronometer: Chronometer,
) : AnswerTimer.Display {
    private val context: Context
        get() = chronometer.context

    override var visibility: Int
        get() = chronometer.visibility
        set(value) {
            chronometer.visibility = value
        }

    override var base: Long
        get() = chronometer.base
        set(value) {
            chronometer.base = value
        }

    override fun start() = chronometer.start()

    override fun stop() = chronometer.stop()

    override fun resetColor() = chronometer.setTextColor(MaterialColors.getColor(context, android.R.attr.textColor, 0))

    override fun highlightLimit() = chronometer.setTextColor(MaterialColors.getColor(context, CommonR.attr.maxTimerColor, 0))

    override fun setOnTickListener(listener: () -> Unit) {
        chronometer.setOnChronometerTickListener { listener() }
    }
}
