// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.app.Activity
import android.content.Intent
import android.view.View
import android.view.View.MeasureSpec
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.android.view.locationInWindow
import com.ichi2.testutils.dispatchInsets
import com.ichi2.utils.dp
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Keyboard coverage for the remaining screens reported in Issue 21794. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w400dp-h800dp")
class KeyboardInsetsTest : RobolectricTest() {
    @Test
    fun `manage fields keeps the list and add button above the keyboard`() {
        val noteType = getCurrentDatabaseNoteTypeCopy("Basic")
        val activity =
            startActivityNormallyOpenCollectionWithIntent(
                NoteTypeFieldEditor::class.java,
                Intent().apply {
                    putExtra(NoteTypeFieldEditor.EXTRA_NOTETYPE_NAME, noteType.name)
                    putExtra(NoteTypeFieldEditor.EXTRA_NOTETYPE_ID, noteType.id)
                },
            )

        activity.assertContentFollowsKeyboard(
            activity.findViewById(R.id.fields),
            activity.findViewById(R.id.btn_add),
        )
    }

    @Test
    fun `deck overview keeps its content above the keyboard`() {
        val deckId = addDeck("Deck", setAsSelected = true)
        addNoteToDeck(deckId, count = 5)
        val activity = startActivityNormallyOpenCollectionWithIntent(StudyOptionsActivity::class.java, Intent())

        activity.assertContentFollowsKeyboard(
            activity.findViewById(R.id.studyoptions_scrollview),
            activity.findViewById(R.id.studyoptions_start),
        )
    }

    @Test
    @Config(qualifiers = "sw700dp-w1280dp-h800dp")
    fun `tablet deck overview keeps its content above the keyboard`() =
        withDeckPicker(deckCount = 1, withCards = true) { activity ->
            activity.assertContentFollowsKeyboard(
                activity.findViewById(R.id.studyoptions_scrollview),
                activity.findViewById(R.id.studyoptions_start),
            )
        }

    /** Checks actual view bounds as the keyboard opens and closes. */
    private fun Activity.assertContentFollowsKeyboard(vararg views: View) {
        val content = findViewById<View>(android.R.id.content)

        fun layoutContent() {
            advanceRobolectricLooper()
            content.measure(
                MeasureSpec.makeMeasureSpec(content.width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(content.height, MeasureSpec.EXACTLY),
            )
            content.layout(0, 0, content.measuredWidth, content.measuredHeight)
        }

        fun bottomClearances(): List<Int> =
            views.map { view ->
                check(view.isShown && view.height > 0) { "The content must be visible before checking its position" }
                content.locationInWindow().y + content.height - (view.locationInWindow().y + view.height)
            }

        dispatchInsets(navBarBottom = 48.dp)
        layoutContent()
        val beforeKeyboard = bottomClearances()

        dispatchInsets(navBarBottom = 48.dp, imeBottom = 300.dp)
        layoutContent()
        bottomClearances().forEachIndexed { index, clearance ->
            assertThat("content clears the keyboard", clearance, greaterThanOrEqualTo(300.dp.toPx(this)))
            assertThat("content rises by the additional inset", clearance, equalTo(beforeKeyboard[index] + 252.dp.toPx(this)))
        }

        dispatchInsets(navBarBottom = 48.dp)
        layoutContent()
        assertThat("content returns to its original position", bottomClearances(), equalTo(beforeKeyboard))
    }
}
