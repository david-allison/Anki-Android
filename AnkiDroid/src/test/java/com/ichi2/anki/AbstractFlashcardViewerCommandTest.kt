// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.cardviewer.ViewerCommand.TOGGLE_FLAG_BLUE
import com.ichi2.anki.cardviewer.ViewerCommand.TOGGLE_FLAG_GREEN
import com.ichi2.anki.cardviewer.ViewerCommand.TOGGLE_FLAG_ORANGE
import com.ichi2.anki.cardviewer.ViewerCommand.TOGGLE_FLAG_PINK
import com.ichi2.anki.cardviewer.ViewerCommand.TOGGLE_FLAG_PURPLE
import com.ichi2.anki.cardviewer.ViewerCommand.TOGGLE_FLAG_RED
import com.ichi2.anki.cardviewer.ViewerCommand.TOGGLE_FLAG_TURQUOISE
import com.ichi2.anki.cardviewer.ViewerCommand.UNSET_FLAG
import com.ichi2.anki.cardviewer.ViewerRefresh
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.utils.ext.flag
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric

@RunWith(AndroidJUnit4::class)
class AbstractFlashcardViewerCommandTest : RobolectricTest() {
    @Test
    fun testRefreshIfRequired() {
        // Create an ActivityController instance for AbstractFlashcardViewer
        Robolectric.buildActivity(AbstractFlashcardViewerTest.NonAbstractFlashcardViewer::class.java).use { controller ->
            // Case 1: Activity is resuming, refreshRequired is not null
            with(controller.create().start().get()) {
                refreshRequired = updateCard
                controller.resume() // Ensure the activity is resumed before calling refreshIfRequired
                refreshIfRequired(isResuming = true)
                // Assert that refreshRequired is set to null
                assertThat(refreshRequired, nullValue())
            }

            // Case 2: Activity is not resuming, lifecycle is at least RESUMED, refreshRequired is not null
            with(controller.get()) {
                refreshRequired = updateCard
                controller.resume() // Ensure the activity is resumed before calling refreshIfRequired
                refreshIfRequired(isResuming = false)
                // Assert that refreshRequired is set to null
                assertThat(refreshRequired, nullValue())
            }

            // Case 3: Activity is not resuming, lifecycle is not at least RESUMED, refreshRequired is not null
            with(controller.get()) {
                refreshRequired = updateCard
                controller.pause() // Ensure the activity is paused before calling refreshIfRequired
                refreshIfRequired(isResuming = false)
                // Assert that refreshRequired is not set to null
                assertThat(refreshRequired, notNullValue())
            }
        }
    }

    @Test
    fun flagCommandsReachTheCardUpdate() {
        val viewer = createViewer()
        val flags =
            listOf(
                TOGGLE_FLAG_RED to Flag.RED,
                TOGGLE_FLAG_ORANGE to Flag.ORANGE,
                TOGGLE_FLAG_GREEN to Flag.GREEN,
                TOGGLE_FLAG_BLUE to Flag.BLUE,
                TOGGLE_FLAG_PINK to Flag.PINK,
                TOGGLE_FLAG_TURQUOISE to Flag.TURQUOISE,
                TOGGLE_FLAG_PURPLE to Flag.PURPLE,
            )
        for ((command, flag) in flags) {
            viewer.executeCommand(command)
            assertThat(viewer.lastFlag, equalTo(flag))
            viewer.executeCommand(command)
            assertThat(viewer.lastFlag, equalTo(Flag.NONE))
        }
        viewer.executeCommand(TOGGLE_FLAG_RED)
        viewer.executeCommand(UNSET_FLAG)
        assertThat(viewer.lastFlag, equalTo(Flag.NONE))
    }

    companion object {
        val updateCard = ViewerRefresh(queues = true, note = true, card = true)
    }

    private fun createViewer(): CommandTestCardViewer = CommandTestCardViewer(cardWith(Flag.NONE))

    private fun cardWith(
        @Suppress("SameParameterValue") flag: Flag,
    ): Card {
        val c = mock(Card::class.java)
        val flags = arrayOf(flag.code)
        whenever(c.flag).then { flags[0] }
        doAnswer { invocation: InvocationOnMock ->
            flags[0] = invocation.getArgument(0)
        }.whenever(c).setUserFlag(anyOrNull())
        return c
    }

    private class CommandTestCardViewer(
        private var currentCardOverride: Card?,
    ) : Reviewer() {
        var lastFlag = Flag.NONE
            private set

        override var currentCard: Card?
            get() = currentCardOverride
            set(card) {
                // we don't have getCol() here and we don't need the additional sound processing.
                currentCardOverride = card
            }

        override fun performReload() {
            // intentionally blank
        }

        override fun onFlag(
            card: Card?,
            flag: Flag,
        ) {
            lastFlag = flag
            currentCard!!.setUserFlag(flag.code)
        }
    }
}
