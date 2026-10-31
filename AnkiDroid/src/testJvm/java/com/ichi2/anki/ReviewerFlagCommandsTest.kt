// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.cardviewer.ViewerCommand.TOGGLE_FLAG_BLUE
import com.ichi2.anki.cardviewer.ViewerCommand.TOGGLE_FLAG_GREEN
import com.ichi2.anki.cardviewer.ViewerCommand.TOGGLE_FLAG_ORANGE
import com.ichi2.anki.cardviewer.ViewerCommand.TOGGLE_FLAG_PINK
import com.ichi2.anki.cardviewer.ViewerCommand.TOGGLE_FLAG_PURPLE
import com.ichi2.anki.cardviewer.ViewerCommand.TOGGLE_FLAG_RED
import com.ichi2.anki.cardviewer.ViewerCommand.TOGGLE_FLAG_TURQUOISE
import com.ichi2.anki.cardviewer.ViewerCommand.UNSET_FLAG
import com.ichi2.anki.cardviewer.flagAfter
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test

class ReviewerFlagCommandsTest {
    @Test
    fun doubleTapSetsNone() {
        val viewer = createViewer()
        viewer.executeCommand(TOGGLE_FLAG_RED)
        viewer.executeCommand(TOGGLE_FLAG_RED)

        assertThat(viewer.lastFlag, equalTo(Flag.NONE))
    }

    @Test
    fun noneDoesNothing() {
        val viewer = createViewer()

        viewer.executeCommand(UNSET_FLAG)

        assertThat(viewer.lastFlag, equalTo(Flag.NONE))
    }

    @Test
    fun doubleNoneDoesNothing() {
        val viewer = createViewer()

        viewer.executeCommand(UNSET_FLAG)
        viewer.executeCommand(UNSET_FLAG)

        assertThat(viewer.lastFlag, equalTo(Flag.NONE))
    }

    @Test
    fun flagCanBeChanged() {
        val viewer = createViewer()

        viewer.executeCommand(TOGGLE_FLAG_RED)
        viewer.executeCommand(TOGGLE_FLAG_BLUE)

        assertThat(viewer.lastFlag, equalTo(Flag.BLUE))
    }

    @Test
    fun unsetUnsets() {
        val viewer = createViewer()

        viewer.executeCommand(TOGGLE_FLAG_RED)
        viewer.executeCommand(UNSET_FLAG)

        assertThat(viewer.lastFlag, equalTo(Flag.NONE))
    }

    @Test
    fun tapRedFlagSetsRed() {
        val viewer = createViewer()

        viewer.executeCommand(TOGGLE_FLAG_RED)

        assertThat(viewer.lastFlag, equalTo(Flag.RED))
    }

    @Test
    fun tapOrangeFlagSetsOrange() {
        val viewer = createViewer()

        viewer.executeCommand(TOGGLE_FLAG_ORANGE)

        assertThat(viewer.lastFlag, equalTo(Flag.ORANGE))
    }

    @Test
    fun tapGreenFlagSesGreen() {
        val viewer = createViewer()

        viewer.executeCommand(TOGGLE_FLAG_GREEN)

        assertThat(viewer.lastFlag, equalTo(Flag.GREEN))
    }

    @Test
    fun tapBlueFlagSetsBlue() {
        val viewer = createViewer()

        viewer.executeCommand(TOGGLE_FLAG_BLUE)

        assertThat(viewer.lastFlag, equalTo(Flag.BLUE))
    }

    @Test
    fun tapPinkFlagSetsPink() {
        val viewer = createViewer()

        viewer.executeCommand(TOGGLE_FLAG_PINK)

        assertThat(viewer.lastFlag, equalTo(Flag.PINK))
    }

    @Test
    fun tapTurquoiseFlagSetsTurquoise() {
        val viewer = createViewer()

        viewer.executeCommand(TOGGLE_FLAG_TURQUOISE)

        assertThat(viewer.lastFlag, equalTo(Flag.TURQUOISE))
    }

    @Test
    fun tapPurpleFlagSetsPurple() {
        val viewer = createViewer()

        viewer.executeCommand(TOGGLE_FLAG_PURPLE)

        assertThat(viewer.lastFlag, equalTo(Flag.PURPLE))
    }

    @Test
    fun doubleTapUnsets() {
        testDoubleTapUnsets(TOGGLE_FLAG_RED)
        testDoubleTapUnsets(TOGGLE_FLAG_ORANGE)
        testDoubleTapUnsets(TOGGLE_FLAG_GREEN)
        testDoubleTapUnsets(TOGGLE_FLAG_BLUE)
        testDoubleTapUnsets(TOGGLE_FLAG_PINK)
        testDoubleTapUnsets(TOGGLE_FLAG_TURQUOISE)
        testDoubleTapUnsets(TOGGLE_FLAG_PURPLE)
    }

    private fun testDoubleTapUnsets(command: ViewerCommand) {
        val viewer = createViewer()

        viewer.executeCommand(command)
        viewer.executeCommand(command)

        assertThat(command.toString(), viewer.lastFlag, equalTo(Flag.NONE))
    }

    @Test
    fun unrelatedCommandsDoNotChangeFlags() {
        org.junit.Assert.assertNull(ViewerCommand.SHOW_ANSWER.flagAfter(Flag.RED))
    }

    @Test
    fun unsettingDoesNotRequireACurrentCard() {
        assertThat(UNSET_FLAG.flagAfter(null), equalTo(Flag.NONE))
    }

    private fun createViewer() = FlagCommands()

    private class FlagCommands {
        var lastFlag = Flag.NONE
            private set

        fun executeCommand(command: ViewerCommand) {
            lastFlag = requireNotNull(command.flagAfter(lastFlag))
        }
    }
}
