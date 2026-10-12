// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.noteeditor.compose.ComposeNoteEditorActivity
import com.ichi2.anki.settings.Prefs
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerLegacyEditorTest : RobolectricTest() {
    @After
    fun resetFlag() {
        Prefs.isComposeNoteEditorEnabled = false
    }

    @Test
    fun `accessibility long click opens legacy Add without changing the preference`() {
        Prefs.isComposeNoteEditorEnabled = true
        deckPicker {
            floatingActionMenu.showFloatingActionMenu()
            assertTrue(findViewById<View>(R.id.fab_main).performLongClick())
            assertLegacyAdd()
        }
    }

    @Test
    fun `touch long press opens legacy Add only once including after release`() {
        Prefs.isComposeNoteEditorEnabled = true
        deckPicker {
            floatingActionMenu.showFloatingActionMenu()
            val fab = findViewById<View>(R.id.fab_main)
            val downTime = SystemClock.uptimeMillis()

            fun touch(action: Int) {
                val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, fab.width / 2f, fab.height / 2f, 0)
                try {
                    fab.dispatchTouchEvent(event)
                } finally {
                    event.recycle()
                }
            }
            touch(MotionEvent.ACTION_DOWN)
            ShadowLooper.idleMainLooper(ViewConfiguration.getLongPressTimeout().toLong() + 100, TimeUnit.MILLISECONDS)
            assertLegacyAdd()
            touch(MotionEvent.ACTION_UP)
            advanceRobolectricLooper()
            assertNull(shadowOf(this).nextStartedActivity, "Releasing a long press must not also open Compose")
        }
    }

    @Test
    fun `long click has no editor shortcut when Compose is disabled`() {
        Prefs.isComposeNoteEditorEnabled = false
        deckPicker {
            floatingActionMenu.showFloatingActionMenu()
            assertFalse(findViewById<View>(R.id.fab_main).performLongClick())
            assertNull(shadowOf(this).nextStartedActivity)
        }
    }

    @Test
    fun `ordinary Add click still opens Compose`() {
        Prefs.isComposeNoteEditorEnabled = true
        deckPicker {
            floatingActionMenu.showFloatingActionMenu()
            findViewById<View>(R.id.fab_main).performClick()
            advanceRobolectricLooper()
            val launched = assertNotNull(shadowOf(this).nextStartedActivity)
            assertEquals(ComposeNoteEditorActivity::class.java.name, launched.component?.className)
        }
    }

    private fun DeckPicker.assertLegacyAdd() {
        val launched = assertNotNull(shadowOf(this).nextStartedActivity)
        assertEquals(NoteEditorActivity::class.java.name, launched.component?.className)
        assertEquals(
            NoteEditorFragment.Companion.NoteEditorCaller.DECKPICKER.value,
            launched.getIntExtra(NoteEditorFragment.EXTRA_CALLER, -1),
        )
        assertTrue(launched.getBooleanExtra(ComposeNoteEditorActivity.EXTRA_USE_LEGACY, false))
        assertTrue(Prefs.isComposeNoteEditorEnabled)
        assertFalse(floatingActionMenu.isFABOpen)
    }
}
