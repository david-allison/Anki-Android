// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewer

import android.view.View
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.Collection
import com.ichi2.testutils.JvmTest
import com.ichi2.testutils.rules.MockitoCollectionRule
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify

class AnswerTimerTest : JvmTest() {
    private val chronometer = spy(TestTimerDisplay())

    @get:Rule
    val mockColRule = MockitoCollectionRule()
    override val col: Collection
        get() = mockColRule.col

    @Test
    fun disabledTimer() {
        val timer = getTimer()

        val card: Card =
            mock {
                on { shouldShowTimer(any()) } doReturn false
            }

        timer.setupForCard(col, card)

        assertThat("timer should not be enabled", timer.showTimer, equalTo(false))

        verify(chronometer).stop()
        verify(chronometer).visibility
        verify(chronometer).visibility = View.INVISIBLE
        verify(chronometer, never()).start()
    }

    @Test
    fun enabledTimer() {
        val timer = getTimer()

        val card: Card =
            mock {
                on { shouldShowTimer(any()) } doReturn true
                on { timeLimit(any()) } doReturn 12
            }

        timer.setupForCard(col, card)

        assertThat("timer should be enabled", timer.showTimer, equalTo(true))
        assertThat("Time limit should be 12 minutes", timer.limit, equalTo(12))

        verify(chronometer).start()
        verify(chronometer, atLeast(1)).visibility // we call twice due to the else branch
        // already visible: verify(chronometer).visibility = View.VISIBLE
        verify(chronometer, never()).stop()
        verify(chronometer).base = 13
    }

    @Test
    fun toggle() {
        val timer = getTimer()

        val timerCard: Card =
            mock {
                on { shouldShowTimer(any()) } doReturn true
            }

        val nonTimerCard: Card =
            mock {
                on { shouldShowTimer(any()) } doReturn false
            }

        timer.setupForCard(col, timerCard)
        assertThat("timer should be enabled", timer.showTimer, equalTo(true))
        assertThat("chronometer should be visible", chronometer.visibility, equalTo(View.VISIBLE))

        timer.setupForCard(col, nonTimerCard)

        assertThat("timer should not be enabled", timer.showTimer, equalTo(false))
        assertThat("chronometer should not be visible", chronometer.visibility, equalTo(View.INVISIBLE))

        timer.setupForCard(col, timerCard)
        assertThat("timer should be enabled", timer.showTimer, equalTo(true))
        assertThat("chronometer should be visible", chronometer.visibility, equalTo(View.VISIBLE))
    }

    @Test
    fun testNoCrashOnEarlyPauseResume() =
        runTest {
            val timer = getTimer()
            // before we call setupForCard
            timer.pause()
            timer.resume()
        }

    @Test
    fun pauseResumeIfEnabled() =
        runTest {
            val timer = getTimer()

            val timerCard: Card =
                mock {
                    on { shouldShowTimer(col) } doReturn true
                    on { timeLimit(col) } doReturn 1000
                }

            timer.setupForCard(col, timerCard)

            reset(chronometer)
            timer.pause()
            verify(chronometer).stop()
            timer.resume()
            verify(chronometer).start()
        }

    @Test
    fun pauseResumeDoesNotCallStartIfTimeElapsed() =
        runTest {
            val timer = getTimer()

            val timerCard: Card =
                mock {
                    on { shouldShowTimer(any()) } doReturn true
                    on { timeLimit(any()) } doReturn 1000
                    on { timeTaken(any()) } doReturn 1001
                }

            timer.setupForCard(col, timerCard)

            reset(chronometer)
            timer.pause()
            verify(chronometer).stop()
            timer.resume()
            verify(chronometer, never()).start()
        }

    @Test
    fun cardTimerIsRestartedEvenIfDisabled() =
        runTest {
            // The class is responsible for the pause/resume handling of the card, not just the UI element
            // This may be a candidate for later refactoring

            val timer = getTimer()

            val nonTimerCard: Card =
                mock {
                    on { shouldShowTimer(any()) } doReturn false
                }

            timer.setupForCard(col, nonTimerCard)

            timer.pause()
            verify(nonTimerCard).stopTimer()
            verify(nonTimerCard, never()).resumeTimer()
            timer.resume()
            verify(nonTimerCard).resumeTimer()

            verify(chronometer, never()).start()
        }

    @Test
    fun `ticks highlight and stop the display only when the limit is reached`() {
        var now = 13L
        val timer = AnswerTimer(chronometer) { now }
        val card =
            mock<Card> {
                on { shouldShowTimer(any()) } doReturn true
                on { timeLimit(any()) } doReturn 1000
            }
        timer.setupForCard(col, card)
        reset(chronometer)

        now += 999
        chronometer.tick()
        verify(chronometer, never()).highlightLimit()
        verify(chronometer, never()).stop()

        now++
        chronometer.tick()
        verify(chronometer).highlightLimit()
        verify(chronometer).stop()
    }

    private fun getTimer(): AnswerTimer = AnswerTimer(chronometer) { 13L }
}

private class TestTimerDisplay : AnswerTimer.Display {
    private var listener: () -> Unit = {}
    override var visibility: Int = View.VISIBLE
    override var base: Long = 0

    override fun start() = Unit

    override fun stop() = Unit

    override fun resetColor() = Unit

    override fun highlightLimit() = Unit

    override fun setOnTickListener(listener: () -> Unit) {
        this.listener = listener
    }

    fun tick() = listener()
}
