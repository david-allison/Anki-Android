// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewer

import android.os.SystemClock
import android.view.View
import android.widget.Chronometer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.Collection
import com.ichi2.testutils.EmptyApplication
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import kotlin.test.assertEquals
import com.ichi2.anki.common.android.R as CommonR

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class AnswerTimerAndroidTest {
    @Test
    fun `the Chronometer adapter renders and stops the card timer at its limit`() {
        val chronometer = spy(Chronometer(ApplicationProvider.getApplicationContext()))
        val timer = AnswerTimer(chronometer)
        val col = mock<Collection>()
        val enabled =
            mock<Card> {
                on { shouldShowTimer(any()) } doReturn true
                on { timeLimit(any()) } doReturn 1000
            }
        val disabled = mock<Card> { on { shouldShowTimer(any()) } doReturn false }

        timer.setupForCard(col, disabled)
        assertEquals(View.INVISIBLE, chronometer.visibility)
        timer.setupForCard(col, enabled)
        assertEquals(View.VISIBLE, chronometer.visibility)
        assertEquals(SystemClock.elapsedRealtime(), chronometer.base)

        clearInvocations(chronometer)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
        chronometer.onChronometerTickListener.onChronometerTick(chronometer)
        verify(chronometer).stop()
        assertEquals(MaterialColors.getColor(chronometer.context, CommonR.attr.maxTimerColor, 0), chronometer.currentTextColor)
        assertEquals(1000, timer.limit)
        timer.setupForCard(col, disabled)
        assertEquals(View.INVISIBLE, chronometer.visibility)
    }
}
