// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki

import android.content.Intent
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.scheduler.CardAnswer.Rating
import com.ichi2.anki.AbstractFlashcardViewer.Companion.RESULT_NO_MORE_CARDS
import com.ichi2.anki.NavigationDrawerActivity.Companion.EXTRA_STARTED_WITH_SHORTCUT
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.study.currentDeckStudyState
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ShortcutCompletionLifecycleTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.IN_MEMORY_WITH_MEDIA

    @Test
    fun `legacy completion survives the finishing reviewer being destroyed`() =
        runTest {
            getPreferences().edit { putBoolean("new_congrats_screen", false) }
            ShadowToast.reset()
            addBasicNote()
            val controller =
                startActivityControllerNormallyOpenCollectionWithIntent(
                    this@ShortcutCompletionLifecycleTest,
                    Reviewer::class.java,
                    Intent().putExtra(EXTRA_STARTED_WITH_SHORTCUT, true),
                )
            val reviewer = controller.get()
            col.sched.answerCard(assertNotNull(col.sched.card), Rating.EASY)

            reviewer.updateCardAndRedraw()
            assertTrue(reviewer.isFinishing)
            controller.pause().stop().destroy()
            advanceUntilIdle()
            advanceRobolectricLooper()

            assertEquals(reviewer.getString(CommonString.studyoptions_congrats_finished), ShadowToast.getTextOfLatestToast())
        }

    @Test
    fun `reviewer closes even if reading completion data fails`() =
        runTest {
            addBasicNote()
            val reviewer =
                startActivityNormallyOpenCollectionWithIntent(
                    Reviewer::class.java,
                    Intent().putExtra(EXTRA_STARTED_WITH_SHORTCUT, true),
                )
            col.sched.answerCard(assertNotNull(col.sched.card), Rating.EASY)
            mockkStatic(Collection::currentDeckStudyState)
            try {
                every { col.currentDeckStudyState() } throws IllegalStateException("completion read failed")

                assertFailsWith<IllegalStateException> { reviewer.updateCardAndRedraw() }

                assertTrue(reviewer.isFinishing)
                assertEquals(RESULT_NO_MORE_CARDS, shadowOf(reviewer).resultCode)
            } finally {
                unmockkStatic(Collection::currentDeckStudyState)
            }
        }
}
