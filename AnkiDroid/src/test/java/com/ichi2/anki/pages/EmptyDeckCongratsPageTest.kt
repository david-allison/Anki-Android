// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.scheduler.CardAnswer.Rating
import com.ichi2.anki.CommonString
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.libanki.Consts.DEFAULT_DECK_ID
import com.ichi2.anki.observability.undoableOp
import com.ichi2.testutils.launchFragmentInContainer
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class EmptyDeckCongratsPageTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.IN_MEMORY_WITH_MEDIA

    @Before
    fun resetToast() {
        ShadowToast.reset()
    }

    @Test
    fun `an already empty regular deck closes the page on entry`() =
        runTest {
            addDeck("Other")
            launchFragmentInContainer<CongratsPage>(initialState = Lifecycle.State.CREATED).use { scenario ->
                lateinit var activity: FragmentActivity
                scenario.onFragment { activity = it.requireActivity() }

                scenario.moveToState(Lifecycle.State.RESUMED)
                advanceUntilIdle()

                assertEmptyCompletion(activity)
            }
        }

    @Test
    fun `deleting the final card while visible closes the page`() = checkDeletion(stopped = false)

    @Test
    fun `resuming after the final card was deleted closes the page`() = checkDeletion(stopped = true)

    @Test
    fun `stale Custom Study action rechecks contents before opening the dialog`() =
        runTest {
            addDeck("Other")
            val card = addBasicNote().firstCard()
            col.sched.answerCard(assertNotNull(col.sched.card), Rating.EASY)
            launchFragmentInContainer<CongratsPage>().use { scenario ->
                lateinit var activity: FragmentActivity
                scenario.onFragment { activity = it.requireActivity() }
                advanceUntilIdle()
                // Deliberately omit the change notification: the action must recheck its own target.
                col.removeNotes(cardIds = listOf(card.id))

                scenario.onFragment { it.bridgeCommands.getValue("customStudy").invoke() }
                advanceUntilIdle()

                assertEmptyCompletion(activity)
            }
        }

    @Test
    fun `empty filtered deck keeps the completion page`() =
        runTest {
            addDynamicDeck("Filtered", "deck:Missing")
            launchFragmentInContainer<CongratsPage>().use { scenario ->
                advanceUntilIdle()

                scenario.onFragment {
                    assertFalse(it.requireActivity().isFinishing)
                    assertNull(shadowOf(it.requireActivity()).nextStartedActivity)
                }
            }
        }

    private fun checkDeletion(stopped: Boolean) =
        runTest {
            addDeck("Other")
            val card = addBasicNote().firstCard()
            col.sched.answerCard(assertNotNull(col.sched.card), Rating.EASY)
            launchFragmentInContainer<CongratsPage>().use { scenario ->
                lateinit var activity: FragmentActivity
                scenario.onFragment { activity = it.requireActivity() }
                advanceUntilIdle()
                assertFalse(activity.isFinishing)
                if (stopped) scenario.moveToState(Lifecycle.State.CREATED)

                undoableOp { removeNotes(cardIds = listOf(card.id)) }
                val undo = col.undoStatus()
                if (stopped) scenario.moveToState(Lifecycle.State.RESUMED)
                advanceUntilIdle()

                assertEmptyCompletion(activity)
                assertEquals(DEFAULT_DECK_ID, col.decks.selected())
                assertEquals(undo, col.undoStatus())
            }
        }

    private fun assertEmptyCompletion(activity: FragmentActivity) {
        assertTrue(activity.isFinishing)
        assertNull(shadowOf(activity).nextStartedActivity)
        assertEquals(activity.getString(CommonString.empty_deck), ShadowToast.getTextOfLatestToast())
    }
}
