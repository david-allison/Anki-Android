// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.test.core.app.ActivityScenario
import app.cash.turbine.test
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.DeckPickerFloatingActionMenu.CloseAnimation
import com.ichi2.anki.navigation.AnkiDroidNavigator
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.testutils.ext.text
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.nullValue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/** Floating-action-menu scenarios in both phone and tablet layouts. */
@RunWith(ParameterizedRobolectricTestRunner::class)
class DeckPickerFloatingActionMenuTest : DeckPickerLayoutTest() {
    @Test
    fun `baseSnackbarBuilder has no anchor when FAB is hidden`() =
        deckPicker {
            val fab = findViewById<View>(R.id.fab_main)
            fab.visibility = View.GONE

            val snackbar = showSnackbar("test")

            snackbar?.let { baseSnackbarBuilder.invoke(it) }

            assertThat(
                "anchorView must be null when FAB is not visible",
                snackbar?.anchorView,
                nullValue(),
            )
        }

    @Test
    fun `baseSnackbarBuilder anchors to FAB when visible`() =
        deckPicker {
            val fab = findViewById<View>(R.id.fab_main)
            fab.visibility = View.VISIBLE

            val snackbar = showSnackbar("test")
            snackbar?.let { baseSnackbarBuilder.invoke(it) }

            assertThat(
                "anchorView is the FAB when visible",
                snackbar?.anchorView,
                equalTo(fab),
            )
        }

    @Test
    fun `FAB opens menu on accessibility click`() =
        deckPicker {
            val fab = findViewById<View>(R.id.fab_main)
            assertThat("menu starts closed", floatingActionMenu.isFABOpen, equalTo(false))
            // TalkBack activate a focused control, which routes to [View.performClick]
            fab.performClick()
            assertThat("FAB menu opens on click", floatingActionMenu.isFABOpen, equalTo(true))
        }

    @Test
    fun `adding a note by single tap preserves the visible FAB menu`() = assertAddingNoteByTouchPreservesMenu(doubleTap = false)

    @Test
    fun `adding a note by double tap preserves the visible FAB menu`() = assertAddingNoteByTouchPreservesMenu(doubleTap = true)

    private fun assertAddingNoteByTouchPreservesMenu(doubleTap: Boolean) =
        deckPicker {
            val fab = floatingActionButtonBinding.fabMain
            val startTime = SystemClock.uptimeMillis()

            fun tap(offset: Long) {
                val downTime = startTime + offset
                for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                    val eventTime = downTime + if (action == MotionEvent.ACTION_UP) 50 else 0
                    val event = MotionEvent.obtain(downTime, eventTime, action, 0f, 0f, 0)
                    try {
                        fab.dispatchTouchEvent(event)
                    } finally {
                        event.recycle()
                    }
                }
            }

            if (!doubleTap) floatingActionMenu.showFloatingActionMenu()
            viewModel.flowOfNavigate.test {
                if (doubleTap) {
                    tap(0)
                    assertTrue(floatingActionMenu.isFABOpen)
                }
                tap(100)
                val intent = AnkiDroidNavigator.toIntent(awaitItem())
                assertEquals("com.ichi2.anki.NoteEditorActivity", intent.component!!.className)
                assertFalse(floatingActionMenu.isFABOpen, "Adding a note must save a closed menu state")
                assertTrue(floatingActionButtonBinding.fabMain.isExtended)
                assertEquals(View.VISIBLE, floatingActionButtonBinding.fabBGLayout.visibility)
            }
        }

    @Test
    fun `selecting Add during FAB reopening keeps menu buttons visible`() =
        deckPicker {
            floatingActionMenu.showFloatingActionMenu()
            shadowOf(mainLooper).idleFor(java.time.Duration.ofMillis(200))
            floatingActionMenu.closeFloatingActionMenu(style = CloseAnimation.SHORT)
            shadowOf(mainLooper).idleFor(java.time.Duration.ofMillis(200))
            assertEquals(View.GONE, floatingActionButtonBinding.addDeckButton.visibility)

            floatingActionMenu.showFloatingActionMenu()
            // Select Add before the opening animations have finished.
            floatingActionButtonBinding.fabMain.performClick()
            shadowOf(mainLooper).idleFor(java.time.Duration.ofMillis(200))

            with(floatingActionButtonBinding) {
                assertTrue(fabMain.isExtended)
                assertEquals(View.VISIBLE, addDeckButton.visibility)
                assertEquals(View.VISIBLE, addFilteredDeckButton.visibility)
                assertEquals(View.VISIBLE, addSharedButton.visibility)
            }
        }

    @Test
    fun `Add menu resets on resume without stopping in multi-window`() {
        ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
            val events = mutableListOf<Lifecycle.Event>()
            scenario.onActivity { deckPicker ->
                deckPicker.lifecycle.addObserver(LifecycleEventObserver { _, event -> events.add(event) })
                deckPicker.floatingActionMenu.showFloatingActionMenu()
                advanceRobolectricLooper()
                with(deckPicker.floatingActionButtonBinding) {
                    val expandedColor = fabMain.backgroundTintList
                    fabMain.performClick()
                    assertTrue(fabMain.isExtended)
                    assertEquals(expandedColor, fabMain.backgroundTintList)
                    assertEquals(View.VISIBLE, fabBGLayout.visibility)
                }
            }

            // A visible activity can be paused without being stopped in multi-window mode.
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.onActivity { deckPicker ->
                assertTrue(deckPicker.floatingActionButtonBinding.fabMain.isExtended)
            }
            scenario.moveToState(Lifecycle.State.RESUMED)

            scenario.onActivity { deckPicker ->
                assertTrue(Lifecycle.Event.ON_PAUSE in events)
                assertFalse(Lifecycle.Event.ON_STOP in events)
                with(deckPicker.floatingActionButtonBinding) {
                    assertFalse(fabMain.isExtended)
                    assertEquals(View.GONE, fabBGLayout.visibility)
                    assertEquals(View.GONE, addDeckButton.visibility)
                    assertEquals(View.GONE, addFilteredDeckButton.visibility)
                    assertEquals(View.GONE, addSharedButton.visibility)
                    fabMain.performClick()
                    assertTrue(deckPicker.floatingActionMenu.isFABOpen)
                    assertEquals(View.VISIBLE, fabBGLayout.visibility)
                }
            }
        }
    }

    @Test
    fun `FAB menu opens on ENTER key`() =
        deckPicker {
            val fab = findViewById<View>(R.id.fab_main)

            assertThat("menu starts closed", floatingActionMenu.isFABOpen, equalTo(false))

            fab.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))

            assertThat("ENTER key opens the FAB menu", floatingActionMenu.isFABOpen, equalTo(true))
        }

    @Test
    fun `FAB menu closes on ESCAPE key`() =
        deckPicker {
            val fab = findViewById<View>(R.id.fab_main)
            floatingActionMenu.showFloatingActionMenu()
            assertThat("menu is open", floatingActionMenu.isFABOpen, equalTo(true))

            fab.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE))

            assertThat(
                "ESCAPE key closes the FAB menu",
                floatingActionMenu.isFABOpen,
                equalTo(false),
            )
        }

    @Test
    fun `FAB menu closes when clicking background inside menu container`() =
        deckPicker {
            floatingActionMenu.showFloatingActionMenu()
            assertThat("menu is open", floatingActionMenu.isFABOpen, equalTo(true))

            // Click the background inside the FAB menu container
            // (simulating, for example, tapping to the left of narrower buttons such as the "Add" button)
            val linearLayout = floatingActionButtonBinding.fabLinearLayout
            linearLayout.performClick()

            assertThat(
                "clicking the background inside the FAB menu container closes the menu",
                floatingActionMenu.isFABOpen,
                equalTo(false),
            )
        }

    @Test
    fun `expanded FAB menu is restored after recreation`() {
        ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
            lateinit var originalActivity: DeckPicker
            scenario.onActivity { deckPicker ->
                originalActivity = deckPicker
                deckPicker.floatingActionMenu.showFloatingActionMenu()
            }

            scenario.recreate()
            advanceRobolectricLooper()

            scenario.onActivity { deckPicker ->
                assertNotSame(originalActivity, deckPicker)
                assertTrue(deckPicker.floatingActionMenu.isFABOpen)
                with(deckPicker.floatingActionButtonBinding) {
                    assertEquals(View.VISIBLE, addSharedButton.visibility)
                    assertEquals(View.VISIBLE, addDeckButton.visibility)
                    assertEquals(View.VISIBLE, addFilteredDeckButton.visibility)
                }

                deckPicker.invalidateOptionsMenu()
                advanceRobolectricLooper()
                assertTrue(deckPicker.floatingActionMenu.isFABOpen)

                deckPicker.onBackPressedDispatcher.onBackPressed()

                assertFalse(deckPicker.floatingActionMenu.isFABOpen)
            }
        }
    }

    @Test
    fun `expanding the FAB menu shows the correct labels`() =
        deckPicker {
            floatingActionMenu.showFloatingActionMenu()
            advanceRobolectricLooper()

            val binding = floatingActionButtonBinding
            assertThat(binding.fabMain.text.toString(), equalTo(getString(CommonString.menu_add)))
            assertThat(
                binding.addSharedButton.text.toString(),
                equalTo(getString(CommonString.menu_get_shared_decks)),
            )
            assertThat(
                binding.addFilteredDeckButton.text.toString(),
                equalTo(getString(CommonString.new_dynamic_deck)),
            )
            // 'Create deck' uses a backend string rather than an android:text resource
            assertThat(
                binding.addDeckButton.text.toString(),
                equalTo(with(targetContext) { TR.sentenceCase.createDeck }),
            )
        }

    @Test
    fun `expanding the FAB menu re-extends the Create deck button`() =
        deckPicker {
            val addDeckButton = floatingActionButtonBinding.addDeckButton
            addDeckButton.isExtended = false

            floatingActionMenu.showFloatingActionMenu()
            advanceRobolectricLooper()

            assertTrue(!addDeckButton.text.isNullOrBlank(), "Create deck button must have a label")
            assertTrue(
                addDeckButton.isExtended,
                "Create deck button must be extended so its label is visible",
            )
        }
}
