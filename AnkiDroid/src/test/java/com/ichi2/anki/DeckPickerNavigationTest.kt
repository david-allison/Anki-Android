// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.text.Layout
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.View.MeasureSpec
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commitNow
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.SdkSuppress
import app.cash.turbine.test
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.ichi2.anki.DeckPickerTest.DeckPickerEx
import com.ichi2.anki.browser.CardBrowserFragment
import com.ichi2.anki.browser.CardBrowserViewModel.RowSelection
import com.ichi2.anki.databinding.ActivityHomescreenBinding
import com.ichi2.anki.deckpicker.DeckPickerViewModel
import com.ichi2.anki.dialogs.DeckSelectionDialog
import com.ichi2.anki.dialogs.SyncErrorDialog
import com.ichi2.anki.dialogs.utils.title
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.anki.reviewreminders.ScheduleRemindersFragment
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.ui.RecyclerFastScroller
import com.ichi2.testutils.dispatchTouch
import com.ichi2.testutils.ext.addBasicNoteWithOp
import com.ichi2.testutils.ext.menu
import com.ichi2.testutils.ext.text
import com.ichi2.testutils.withBooleanPreference
import com.ichi2.ui.AccessibleSearchView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.runner.RunWith
import org.mockito.Mockito.times
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Pane and navigation regressions share the same phone/tablet fixture as DeckPickerTest. */
@RunWith(ParameterizedRobolectricTestRunner::class)
class DeckPickerNavigationTest : DeckPickerLayoutTest() {
    private fun keyDownEvent(
        keyCode: Int,
        modifiers: Int,
    ): KeyEvent = KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0, modifiers)

    @Test
    fun checkDisplayOfStudyOptionsOnTablet() {
        assumeTrue("We are running on a tablet", qualifiers!!.contains("xlarge"))
        val deckPickerEx =
            super.startActivityNormallyOpenCollectionWithIntent(
                DeckPickerEx::class.java,
                Intent(),
            )
        val studyOptionsFragment =
            deckPickerEx.supportFragmentManager.findFragmentById(R.id.studyoptions_fragment) as StudyOptionsFragment?
        assertThat(
            "Study options should show on start on tablet",
            studyOptionsFragment,
            notNullValue(),
        )
        assertThat(
            "DeckPicker should use the fragmented layout on tablet",
            deckPickerEx.fragmented,
            equalTo(true),
        )
    }

    // Menu invalidation can replace the action view, so resolve it on each access.
    private val DeckPicker.searchView: AccessibleSearchView
        get() = menu().findItem(R.id.deck_picker_action_filter).actionView as AccessibleSearchView

    @Test
    fun `dragging next to the tablet divider resizes the panes`() {
        assumeTrue("We are running on a tablet", qualifiers!!.contains("xlarge"))
        addBasicNote()
        val deckPicker = super.startActivityNormallyOpenCollectionWithIntent(DeckPickerEx::class.java, Intent())
        advanceRobolectricLooper()
        val divider = deckPicker.findViewById<View>(R.id.resizing_divider)
        val deckPickerPane = deckPicker.findViewById<View>(R.id.deck_picker_pane)
        val weightBefore = (deckPickerPane.layoutParams as LinearLayout.LayoutParams).weight
        val location = IntArray(2).also { divider.getLocationInWindow(it) }
        val startX = location[0] + divider.width / 2f - 16 * divider.resources.displayMetrics.density
        val y = location[1] + divider.height / 2f
        val window = deckPicker.window.decorView
        val downTime = SystemClock.uptimeMillis()

        window.dispatchTouchEvent(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, startX, y, 0))
        for (step in 1..10) {
            window.dispatchTouchEvent(
                MotionEvent.obtain(downTime, downTime + step * 10L, MotionEvent.ACTION_MOVE, startX + step * 10f, y, 0),
            )
        }
        window.dispatchTouchEvent(MotionEvent.obtain(downTime, downTime + 200, MotionEvent.ACTION_UP, startX + 100f, y, 0))

        val weightAfter = (deckPickerPane.layoutParams as LinearLayout.LayoutParams).weight
        assertThat("the deck list grows when dragged towards the end", weightAfter, greaterThan(weightBefore))
    }

    @Test
    fun `deck search stays open while filtering`() {
        addBasicNote()
        repeat(10) { addDeck("Test Deck $it") }

        deckPicker {
            val searchItem = menu().findItem(R.id.deck_picker_action_filter)
            assertTrue(searchItem.isVisible)
            assertTrue(searchItem.expandActionView())

            for ((query, expectedCount) in listOf("s" to 10, "Test Deck 1" to 1, "missing" to 0, "" to 11)) {
                searchView.setQuery(query, false)
                advanceRobolectricLooperUntil { visibleDeckCount == expectedCount }

                val currentSearchItem = menu().findItem(R.id.deck_picker_action_filter)
                assertTrue(currentSearchItem.isActionViewExpanded, "Search should stay open for '$query'")
                assertEquals(query, searchView.query.toString())
            }
        }
    }

    @Test
    fun `deck search query is restored after recreation`() {
        addBasicNote()
        repeat(10) { addDeck("Test Deck $it") }

        ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
            lateinit var retainedViewModel: DeckPickerViewModel
            scenario.onActivity { deckPicker ->
                advanceRobolectricLooper()
                retainedViewModel = deckPicker.viewModel
                val searchItem = deckPicker.menu().findItem(R.id.deck_picker_action_filter)
                assertTrue(searchItem.expandActionView())
                deckPicker.searchView.setQuery("Test Deck 1", false)
                advanceRobolectricLooperUntil { deckPicker.visibleDeckCount == 1 }
            }

            scenario.recreate()
            advanceRobolectricLooper()

            scenario.onActivity { deckPicker ->
                assertSame(retainedViewModel, deckPicker.viewModel)
                assertEquals("Test Deck 1", deckPicker.viewModel.deckSearchQuery)
                val searchItem = deckPicker.menu().findItem(R.id.deck_picker_action_filter)
                assertTrue(searchItem.isActionViewExpanded)
                assertEquals("Test Deck 1", deckPicker.searchView.query.toString())
                advanceRobolectricLooperUntil { deckPicker.visibleDeckCount == 1 }
                deckPicker.invalidateOptionsMenu()
            }
            advanceRobolectricLooper()

            scenario.onActivity { deckPicker ->
                val searchItem = deckPicker.menu().findItem(R.id.deck_picker_action_filter)
                assertTrue(searchItem.isActionViewExpanded)
                assertEquals("Test Deck 1", deckPicker.searchView.query.toString())
                assertEquals(1, deckPicker.visibleDeckCount)
                assertTrue(searchItem.collapseActionView())
                assertFalse(deckPicker.viewModel.isDeckSearchExpanded)
                assertEquals("", deckPicker.viewModel.deckSearchQuery)
                advanceRobolectricLooperUntil { deckPicker.visibleDeckCount == 11 }
            }
        }
    }

    @Test
    fun `deck search query is restored before the queued menu refresh`() =
        runTest {
            addBasicNote()
            repeat(10) { addDeck("Test Deck $it") }
            val deckPicker = startRegularActivity<DeckPicker>()
            advanceRobolectricLooper()
            val menu = deckPicker.menu()
            val searchItem = menu.findItem(R.id.deck_picker_action_filter)
            assertTrue(searchItem.expandActionView())
            deckPicker.searchView.setQuery("Test Deck 1", false)
            advanceRobolectricLooperUntil { deckPicker.visibleDeckCount == 1 }

            // A synchronous collection refresh restores the query a second time, masking
            // SearchView clearing it during expansion. Exercise the production queue instead.
            val previousQueue = CollectionManager.setTestDispatcher(StandardTestDispatcher(testScheduler), useReentrantLock = false)
            try {
                menu.clear()
                deckPicker.onCreateOptionsMenu(menu)
                assertFalse(assertNotNull(deckPicker.createMenuJob).isCompleted)

                val restoredSearchItem = menu.findItem(R.id.deck_picker_action_filter)
                assertTrue(restoredSearchItem.isActionViewExpanded)
                assertEquals("Test Deck 1", deckPicker.searchView.query.toString())
                advanceRobolectricLooper()
                assertEquals("Test Deck 1", deckPicker.viewModel.deckSearchQuery)

                advanceUntilIdle()
                advanceRobolectricLooperUntil { deckPicker.visibleDeckCount == 1 }
            } finally {
                CollectionManager.setTestDispatcher(previousQueue)
            }
        }

    @Test
    fun `empty expanded deck search is restored after recreation`() {
        addBasicNote()
        repeat(10) { addDeck("Test Deck $it") }

        ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
            scenario.onActivity { deckPicker ->
                advanceRobolectricLooper()
                assertTrue(deckPicker.menu().findItem(R.id.deck_picker_action_filter).expandActionView())
            }

            scenario.recreate()
            advanceRobolectricLooper()

            scenario.onActivity { deckPicker ->
                val searchItem = deckPicker.menu().findItem(R.id.deck_picker_action_filter)
                assertTrue(searchItem.isActionViewExpanded)
                assertEquals("", deckPicker.searchView.query.toString())
                assertTrue(searchItem.collapseActionView())
            }

            scenario.recreate()
            advanceRobolectricLooper()

            scenario.onActivity { deckPicker ->
                val searchItem = deckPicker.menu().findItem(R.id.deck_picker_action_filter)
                assertFalse(searchItem.isActionViewExpanded)
                advanceRobolectricLooperUntil { deckPicker.visibleDeckCount == 11 }
            }
        }
    }

    @Test
    fun `study options follows the selected deck`() {
        assumeTrue("We are running on a tablet", qualifiers!!.contains("xlarge"))
        addBasicNote()
        val deckId = addDeck("Another Deck")
        addNoteToDeck(deckId, count = 2)

        deckPicker {
            val studyOptions = assertNotNull(fragment)
            val studyOptionsView = studyOptions.requireView()
            viewModel.selectDeck(deckId).join()
            advanceRobolectricLooper()

            assertSame(studyOptions, fragment)
            assertSame(studyOptionsView, assertNotNull(fragment).requireView())
            assertEquals(deckId, assertNotNull(fragment).viewModel.selectedDeckId)
            assertEquals("Another Deck", findViewById<TextView>(R.id.studyoptions_deck_name).text.toString())
            assertEquals("2", findViewById<TextView>(R.id.studyoptions_new_count).text.toString())

            viewModel.selectDeck(1L).join()
            advanceRobolectricLooper()

            assertSame(studyOptionsView, assertNotNull(fragment).requireView())
            assertEquals(1L, studyOptions.viewModel.selectedDeckId)
            assertEquals("1", findViewById<TextView>(R.id.studyoptions_new_count).text.toString())
        }
    }

    @Test
    fun `description wrapping is restored after releasing or cancelling divider drag`() {
        assumeTrue("We are running on a tablet", qualifiers!!.contains("xlarge"))
        addBasicNote()

        deckPicker {
            val description = findViewById<TextView>(R.id.studyoptions_deck_description)
            val defaultDescription =
                layoutInflater.inflate(R.layout.fragment_study_options, null).findViewById<TextView>(R.id.studyoptions_deck_description)
            assertEquals(defaultDescription.breakStrategy, description.breakStrategy)
            assertEquals(defaultDescription.hyphenationFrequency, description.hyphenationFrequency)
            val divider = findViewById<View>(R.id.resizing_divider)

            for (endAction in listOf(MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL)) {
                // Restore the view's own settings, including values different from the theme defaults.
                @SuppressLint("WrongConstant")
                description.breakStrategy = Layout.BREAK_STRATEGY_BALANCED
                description.hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_FULL

                divider.dispatchTouch(MotionEvent.ACTION_DOWN)
                repeat(2) { divider.dispatchTouch(MotionEvent.ACTION_MOVE) }
                assertEquals(Layout.BREAK_STRATEGY_SIMPLE, description.breakStrategy)
                assertEquals(Layout.HYPHENATION_FREQUENCY_NONE, description.hyphenationFrequency)

                divider.dispatchTouch(endAction)
                assertEquals(Layout.BREAK_STRATEGY_BALANCED, description.breakStrategy)
                assertEquals(Layout.HYPHENATION_FREQUENCY_FULL, description.hyphenationFrequency)
            }
        }
    }

    @Test
    fun `study options updates after reloading deck counts`() {
        assumeTrue("We are running on a tablet", qualifiers!!.contains("xlarge"))
        addBasicNote()

        deckPicker {
            val studyOptions = assertNotNull(fragment)
            val initialState = studyOptions.viewModel.state
            assertEquals(1, initialState.dataOrNull()?.numberOfCardsInDeck)

            addBasicNote()
            reloadDecks()

            assertSame(studyOptions, fragment)
            val updatedState = assertNotNull(fragment).viewModel.state
            assertEquals(2, updatedState.dataOrNull()?.numberOfCardsInDeck)
        }
    }

    @Test
    fun `deck reload preserves reminder panel and its dialog`() =
        withReminderPanel { reminders ->
            val dialog = showSyncConflict()

            reloadDecks()

            assertSame(reminders, sidePanel)
            assertTrue(dialog.isShowing)
        }

    @Test
    fun `cancelling sync conflict after reload returns to reminders`() =
        withReminderPanel { reminders ->
            val dialog = showSyncConflict()
            reloadDecks()

            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).performClick()
            advanceRobolectricLooper()

            assertFalse(dialog.isShowing)
            assertSame(reminders, sidePanel)
        }

    @Test
    fun `reminder toolbar returns to study options after reload`() =
        withReminderPanel { reminders ->
            reloadDecks()

            reminders.navigateUp()

            assertNotNull(fragment)
            assertEquals(0, supportFragmentManager.backStackEntryCount)
        }

    @Test
    fun `selecting another deck closes reminders and clears pane navigation history`() =
        withReminderPanel {
            val anotherDeck = addDeck("Another Deck")

            viewModel.selectDeck(anotherDeck).join()
            advanceRobolectricLooper()

            assertEquals(anotherDeck, assertNotNull(fragment).viewModel.selectedDeckId)
            assertEquals(0, supportFragmentManager.backStackEntryCount)
        }

    @Test
    fun `returning from reminders does not accumulate study options subscriptions`() {
        assumeTrue("We are running on a tablet", qualifiers!!.contains("xlarge"))
        addBasicNote()
        Prefs.newReviewRemindersEnabled = true

        deckPicker {
            val studyOptions = assertNotNull(fragment)
            val state = studyOptions.viewModel.flowOfState as MutableStateFlow<StudyOptionsState>
            val initialSubscriberCount = ChangeManager.subscriberCount
            assertEquals(1, state.subscriptionCount.value)

            repeat(2) {
                selectContextMenuOption(ContextMenuOption.SCHEDULE_REMINDERS, studyOptions.viewModel.selectedDeckId)
                advanceRobolectricLooper()
                val reminders = requireNotNull(sidePanel as? ScheduleRemindersFragment)
                reminders.navigateUp()

                assertSame(studyOptions, fragment)
                assertAll(
                    { assertEquals("Active study options state collectors", 1, state.subscriptionCount.value) },
                    { assertEquals("ChangeManager subscriptions", initialSubscriberCount, ChangeManager.subscriberCount) },
                )
            }
        }
    }

    @Test
    fun `returning from reminders restores scroll only for the same deck`() {
        assumeTrue("We are running on a tablet", qualifiers!!.contains("xlarge"))
        addBasicNote()
        val anotherDeck = addDeck("Another deck")
        addNoteToDeck(anotherDeck, count = 2)
        Prefs.newReviewRemindersEnabled = true

        deckPicker {
            makeStudyOptionsScrollable()
            val studyOptions = assertNotNull(fragment)
            for (selectAnotherDeck in listOf(false, true)) {
                val scroll = studyOptions.layoutScroll()
                scroll.scrollTo(0, 200)
                assertEquals(200, scroll.scrollY)

                selectContextMenuOption(ContextMenuOption.SCHEDULE_REMINDERS, studyOptions.viewModel.selectedDeckId)
                advanceRobolectricLooper()
                val reminders = assertNotNull(sidePanel as? ScheduleRemindersFragment)
                if (selectAnotherDeck) {
                    viewModel.selectDeck(anotherDeck).join()
                    advanceRobolectricLooper()
                } else {
                    reminders.navigateUp()
                }

                assertSame(studyOptions, fragment)
                assertEquals(if (selectAnotherDeck) anotherDeck else 1L, studyOptions.viewModel.selectedDeckId)
                val restoredScroll = studyOptions.layoutScroll()
                assertEquals(if (selectAnotherDeck) 0 else 200, restoredScroll.scrollY)
            }
        }
    }

    @Test
    fun `restored study options associates saved scroll with its deck`() {
        assumeTrue("We are running on a tablet", qualifiers!!.contains("xlarge"))
        addBasicNote()
        val anotherDeck = addDeck("Another deck")
        addNoteToDeck(anotherDeck, count = 2)

        deckPicker {
            makeStudyOptionsScrollable()
            for (deckId in listOf(1L, anotherDeck)) {
                val studyOptions = assertNotNull(fragment)
                studyOptions.layoutScroll().scrollTo(0, 200)
                val savedState = supportFragmentManager.saveFragmentInstanceState(studyOptions)
                viewModel.selectDeck(deckId).join()
                advanceRobolectricLooper()

                // Recreate the fragment from its saved state, including its view hierarchy.
                val restored = StudyOptionsFragment().apply { setInitialSavedState(savedState) }
                supportFragmentManager.commitNow { replace(R.id.studyoptions_fragment, restored) }
                advanceRobolectricLooper()

                assertEquals(deckId, restored.viewModel.selectedDeckId)
                assertEquals(if (deckId == 1L) 200 else 0, restored.layoutScroll().scrollY)
            }
        }
    }

    /** Keep original and restored panes scrollable without relying on font metrics. */
    private fun DeckPicker.makeStudyOptionsScrollable() {
        val studyOptionsView = assertNotNull(fragment).requireView()
        studyOptionsView.findViewById<ScrollView>(R.id.studyoptions_scrollview).getChildAt(0).minimumHeight = 10000
        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentViewCreated(
                    fm: FragmentManager,
                    f: Fragment,
                    v: View,
                    savedInstanceState: Bundle?,
                ) {
                    if (f is StudyOptionsFragment) {
                        v.findViewById<ScrollView>(R.id.studyoptions_scrollview).getChildAt(0).minimumHeight = 10000
                    }
                }
            },
            false,
        )
    }

    private fun StudyOptionsFragment.layoutScroll(): ScrollView =
        requireView().findViewById<ScrollView>(R.id.studyoptions_scrollview).apply {
            measure(MeasureSpec.makeMeasureSpec(400, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(300, MeasureSpec.EXACTLY))
            layout(0, 0, 400, 300)
        }

    private fun withReminderPanel(test: suspend DeckPicker.(ScheduleRemindersFragment) -> Unit) {
        assumeTrue("We are running on a tablet", qualifiers!!.contains("xlarge"))
        addBasicNote()
        Prefs.newReviewRemindersEnabled = true
        deckPicker {
            selectContextMenuOption(ContextMenuOption.SCHEDULE_REMINDERS, getColUnsafe.decks.selected())
            advanceRobolectricLooper()
            test(requireNotNull(sidePanel as? ScheduleRemindersFragment))
        }
    }

    private val DeckPicker.sidePanel
        get() = supportFragmentManager.findFragmentById(R.id.studyoptions_fragment)

    private suspend fun DeckPicker.reloadDecks() {
        viewModel.reloadDeckCounts().join()
        advanceRobolectricLooper()
    }

    private fun DeckPicker.showSyncConflict(): AlertDialog {
        showSyncErrorDialog(SyncErrorDialog.Type.DIALOG_SYNC_CONFLICT_RESOLUTION)
        advanceRobolectricLooper()
        return ShadowDialog.getLatestDialog() as AlertDialog
    }

    private fun ScheduleRemindersFragment.navigateUp() {
        val toolbar = requireView().findViewById<Toolbar>(R.id.non_collapsible_toolbar)
        toolbar.children.first { it.contentDescription == toolbar.navigationContentDescription }.performClick()
        advanceRobolectricLooper()
    }

    @Test
    fun `study options menu items are only displayed in fragmented mode`() {
        deckPickerEx {
            val isTablet = fragmented
            val menu = menu()
            if (isTablet) {
                // Ossifies the split-pane menu decision: the side panel fragment contributes
                // its items to this activity's toolbar via its MenuProvider.
                assertThat(
                    "custom study should be displayed in fragmented mode",
                    menu.findItem(R.id.action_custom_study),
                    notNullValue(),
                )
                assertThat(
                    "deck options should be displayed in fragmented mode",
                    menu.findItem(R.id.action_deck_or_study_options),
                    notNullValue(),
                )
            } else {
                // No side panel fragment exists: its items must not appear.
                assertThat(
                    "custom study must not be displayed outside fragmented mode",
                    menu.findItem(R.id.action_custom_study),
                    nullValue(),
                )
                assertThat(
                    "deck options must not be displayed outside fragmented mode",
                    menu.findItem(R.id.action_deck_or_study_options),
                    nullValue(),
                )
            }
        }
    }

    @Test
    fun `restored study options fragment is pruned when recreated into single pane`() {
        assumeTrue("We are running on a tablet", qualifiers!!.contains("xlarge"))
        ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
            lateinit var viewModel: DeckPickerViewModel
            scenario.onActivity { viewModel = it.viewModel }
            viewModel.awaitStartup()
            scenario.onActivity { deckPicker ->
                assertThat(
                    "side panel fragment should be displayed on tablet",
                    deckPicker.supportFragmentManager.findFragmentById(R.id.studyoptions_fragment),
                    notNullValue(),
                )
            }
            // Fold the device: the activity recreates into the single-pane layout, while
            // FragmentManager restores the saved side panel fragment into it.
            RuntimeEnvironment.setQualifiers("sw320dp")
            scenario.recreate()
            advanceRobolectricLooper()
            scenario.onActivity { deckPicker ->
                assertThat(
                    "restored side panel fragment must be pruned in single-pane layout",
                    deckPicker.supportFragmentManager.findFragmentById(R.id.studyoptions_fragment),
                    nullValue(),
                )
                assertThat(
                    "study options menu items must not leak into the single-pane toolbar",
                    deckPicker.menu().findItem(R.id.action_custom_study),
                    nullValue(),
                )
            }
        }
    }

    private fun withBottomNavigationEnabled(action: () -> Unit) = withBooleanPreference(R.string.dev_bottom_nav_key, true, action)

    private fun DeckPicker.openEmbeddedCardBrowser(): CardBrowserFragment {
        findViewById<BottomNavigationView>(R.id.bottom_navigation).selectedItemId =
            BottomNavController.NavigationItem.BROWSER.id
        supportFragmentManager.executePendingTransactions()
        return supportFragmentManager.findFragmentByTag("browser") as CardBrowserFragment
    }

    @Test
    fun `bottom navigation features are disabled on tablets`() =
        withBottomNavigationEnabled {
            deckPicker {
                assumeTrue("Running on tablet", fragmented)

                assertThat(Prefs.devBottomNavEnabled, equalTo(true))
                assertThat(bottomNavigationEnabled, equalTo(false))
                assertThat(resources.getBoolean(R.bool.bottom_navigation_available), equalTo(false))
                val bottomNavigation = ActivityHomescreenBinding.bind(findViewById(R.id.root_layout)).bottomNavigation
                assertThat(bottomNavigation, nullValue())
                assertThat(deckPickerBinding.decks.itemDecorationCount, equalTo(0))
            }
        }

    @Test
    fun `bottom navigation follows the single pane layout on large screens`() =
        withBottomNavigationEnabled {
            assumeTrue("Only run once", qualifiers == "normal")
            RuntimeEnvironment.setQualifiers("sw480dp-large")
            deckPicker {
                assertThat(fragmented, equalTo(false))
                assertThat(Prefs.devBottomNavEnabled, equalTo(true))
                assertThat(bottomNavigationEnabled, equalTo(true))
                assertThat(resources.getBoolean(R.bool.bottom_navigation_available), equalTo(true))
                assertThat(binding.bottomNavigation, notNullValue())
                assertThat(binding.bottomNavigation!!.visibility, equalTo(View.VISIBLE))
                assertThat(deckPickerBinding.decks.itemDecorationCount, equalTo(1))
            }
        }

    @Test
    fun `bottom navigation has correct labels`() =
        withBottomNavigationEnabled {
            assumeTrue("Not running on tablet", qualifiers != "xlarge")
            deckPicker {
                val menu = ActivityHomescreenBinding.bind(findViewById(R.id.root_layout)).bottomNavigation!!.menu
                assertThat(menu.findItem(R.id.nav_home)?.title.toString(), equalTo("Decks"))
                assertThat(menu.findItem(R.id.nav_browser)?.title.toString(), equalTo("Browse"))
                assertThat(menu.findItem(R.id.nav_stats)?.title.toString(), equalTo("Statistics"))
                assertThat(menu.findItem(R.id.nav_more)?.title.toString(), equalTo("More"))
            }
        }

    @Test
    fun `Alt number shortcuts navigate between bottom navigation destinations`() =
        withBottomNavigationEnabled {
            assumeTrue("Not running on tablet", qualifiers != "xlarge")
            deckPicker {
                val bottomNav = ActivityHomescreenBinding.bind(findViewById(R.id.root_layout)).bottomNavigation!!
                val shortcuts =
                    listOf(
                        KeyEvent.KEYCODE_1 to BottomNavController.NavigationItem.HOME,
                        KeyEvent.KEYCODE_2 to BottomNavController.NavigationItem.BROWSER,
                        KeyEvent.KEYCODE_3 to BottomNavController.NavigationItem.STATS,
                        KeyEvent.KEYCODE_4 to BottomNavController.NavigationItem.MORE,
                    )

                shortcuts.forEach { (keyCode, destination) ->
                    val handled = dispatchKeyEvent(keyDownEvent(keyCode, KeyEvent.META_ALT_ON))

                    assertThat("Alt shortcut is handled", handled, equalTo(true))
                    assertThat(bottomNav.selectedItemId, equalTo(destination.id))
                }
            }
        }

    @Test
    @SdkSuppress(minSdkVersion = 26)
    fun `bottom navigation exposes a long title as a tooltip`() =
        withBottomNavigationEnabled {
            assumeTrue("Not running on tablet", qualifiers != "xlarge")
            deckPicker {
                val bottomNav = ActivityHomescreenBinding.bind(findViewById(R.id.root_layout)).bottomNavigation!!
                val longTitle = "Statistikenübersicht"

                bottomNav.menu.findItem(R.id.nav_stats).title = longTitle

                assertThat(bottomNav.findViewById<View>(R.id.nav_stats).tooltipText.toString(), equalTo(longTitle))
            }
        }

    @Test
    fun `bottom navigation supports the public card browser`() =
        withBottomNavigationEnabled {
            withBooleanPreference(R.string.dev_card_browser_search_view, false) {
                assumeTrue("Not running on tablet", qualifiers != "xlarge")
                deckPicker {
                    val browser = openEmbeddedCardBrowser()
                    val browserView = browser.requireView()
                    val browserToolbar = browserView.findViewById<Toolbar>(R.id.toolbar)
                    assertThat(browser.legacySearchView, notNullValue())
                    assertThat(browserToolbar.menu.findItem(R.id.action_search), notNullValue())
                }
            }
        }

    @Test
    fun `embedded card browser accounts for system bar insets`() =
        withBottomNavigationEnabled {
            withBooleanPreference(R.string.dev_card_browser_search_view, false) {
                assumeTrue("Not running on tablet", qualifiers != "xlarge")
                deckPicker {
                    val browserView = openEmbeddedCardBrowser().requireView()

                    val statusBarHeight = 24
                    val insets =
                        WindowInsetsCompat
                            .Builder()
                            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, statusBarHeight, 0, 0))
                            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 48))
                            .build()
                    ViewCompat.dispatchApplyWindowInsets(browserView, insets)

                    assertThat(browserView.findViewById<View>(R.id.toolbar_container).paddingTop, equalTo(statusBarHeight))
                    assertThat(browserView.findViewById<RecyclerFastScroller>(R.id.browser_scroller).handleBottomInset, equalTo(0))
                }
            }
        }

    @Test
    fun `embedded card browser updates selected row count after deselection`() =
        withBottomNavigationEnabled {
            withBooleanPreference(R.string.dev_card_browser_search_view, false) {
                assumeTrue("Not running on tablet", qualifiers != "xlarge")
                deckPicker {
                    addBasicNoteWithOp(fields = listOf("first", "one"))
                    addBasicNoteWithOp(fields = listOf("second", "two"))
                    val browser = openEmbeddedCardBrowser()
                    advanceRobolectricLooper()
                    val viewModel = browser.activityViewModel
                    val toolbarTitle = browser.requireView().findViewById<TextView>(R.id.toolbar_title)
                    val firstRow = RowSelection(viewModel.getRowAtPosition(0), topOffset = 0)
                    val secondRow = RowSelection(viewModel.getRowAtPosition(1), topOffset = 0)

                    viewModel.handleRowLongPress(firstRow).join()
                    viewModel.onTap(secondRow).join()
                    advanceRobolectricLooper()
                    assertThat(toolbarTitle.text.toString(), equalTo("2"))

                    viewModel.onTap(firstRow).join()
                    advanceRobolectricLooper()
                    assertThat(toolbarTitle.text.toString(), equalTo("1"))
                }
            }
        }

    @Test
    fun `embedded card browser can select all decks`() =
        withBottomNavigationEnabled {
            withBooleanPreference(R.string.dev_card_browser_search_view, false) {
                assumeTrue("Not running on tablet", qualifiers != "xlarge")
                deckPicker {
                    val browser = openEmbeddedCardBrowser()

                    browser.childFragmentManager.setFragmentResult(
                        DeckSelectionDialog.REQUEST_SELECT_DECK,
                        Bundle().apply {
                            putParcelable(DeckSelectionDialog.RESULT_SELECTED_DECK, SelectableDeck.AllDecks)
                        },
                    )
                }
            }
        }

    @Test
    fun `bottom navigation shortcuts are registered in keyboard shortcut help`() =
        withBottomNavigationEnabled {
            assumeTrue("Not running on tablet", qualifiers != "xlarge")
            deckPicker {
                val bottomNavigationShortcuts = shortcuts.shortcuts.filter { it.shortcut.startsWith("Alt+") }

                assertThat(
                    bottomNavigationShortcuts.associate { it.shortcut to it.label },
                    equalTo(
                        mapOf(
                            "Alt+1" to "Deck picker",
                            "Alt+2" to "Card browser",
                            "Alt+3" to "Open statistics",
                            "Alt+4" to "More",
                        ),
                    ),
                )
            }
        }
}
