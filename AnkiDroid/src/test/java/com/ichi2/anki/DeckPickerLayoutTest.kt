// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import com.ichi2.anki.dialogs.DeckPickerContextMenu.DeckPickerContextMenuOption
import com.ichi2.anki.dialogs.DeckPickerContextMenuResult
import com.ichi2.anki.dialogs.setDeckPickerContextMenuResult
import com.ichi2.anki.libanki.DeckId
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.hamcrest.Matchers.not
import org.junit.Before
import org.mockito.Mockito.times
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Keep the same setup and layout matrix while independent scenarios use separate workers. */
abstract class DeckPickerLayoutTest : RobolectricTest() {
    @ParameterizedRobolectricTestRunner.Parameter
    @JvmField // required for Parameter
    var qualifiers: String? = null

    companion object {
        @ParameterizedRobolectricTestRunner.Parameters
        @JvmStatic // required for initParameters
        fun initParameters(): Collection<String> = listOf("normal", "xlarge")
    }

    @Before
    fun before() {
        RuntimeEnvironment.setQualifiers(qualifiers)
        setIntroductionSlidesShown(true)
    }

    /** Simulates a selection in the context menu by setting the specific result in FragmentManager */
    protected fun DeckPicker.selectContextMenuOption(
        option: DeckPickerContextMenuOption,
        deckId: DeckId,
    ) = supportFragmentManager.setDeckPickerContextMenuResult(
        DeckPickerContextMenuResult(deckId = deckId, option = option),
    )

    protected suspend fun <T> TestScope.withQueuedCollectionAccess(block: suspend () -> T): T {
        val previousQueue = CollectionManager.setTestDispatcher(StandardTestDispatcher(testScheduler), useReentrantLock = false)
        try {
            return block()
        } finally {
            CollectionManager.setTestDispatcher(previousQueue)
        }
    }
}
