// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.testutils.EmptyApplication
import com.ichi2.testutils.parcelledCopy
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
class BrowserColumnSelectionArgumentsTest {
    @Test
    fun `column selection mode survives Android argument transport`() {
        for (mode in CardsOrNotes.entries) {
            val arguments = BrowserColumnSelectionFragment.createInstance(mode).requireArguments().parcelledCopy(javaClass.classLoader)
            val restored = BrowserColumnSelectionFragment().apply { this.arguments = arguments }
            assertEquals(mode, restored.cardsOrNotes)
        }
    }
}
