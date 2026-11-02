// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.view.KeyEvent
import android.view.Menu
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.net.toUri
import androidx.core.view.ContentInfoCompat
import androidx.core.view.ViewCompat
import androidx.core.view.children
import androidx.core.view.size
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withText
import anki.backend.backendError
import anki.collection.opChanges
import anki.scheduler.CardAnswer.Rating
import app.cash.turbine.test
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.deckpicker.DeckFilters
import com.ichi2.anki.deckpicker.DeckPickerViewModel
import com.ichi2.anki.deckpicker.SyncIconState
import com.ichi2.anki.deckpicker.filterAndFlattenDisplay
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.dialogs.DeckPickerConfirmDeleteDeckDialog
import com.ichi2.anki.dialogs.DeckPickerContextMenu.DeckPickerContextMenuOption
import com.ichi2.anki.dialogs.utils.input
import com.ichi2.anki.dialogs.utils.message
import com.ichi2.anki.dialogs.utils.performPositiveClick
import com.ichi2.anki.dialogs.utils.title
import com.ichi2.anki.libanki.Consts.DEFAULT_DECK_ID
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.navigation.AnkiDroidNavigator
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.anki.pages.CongratsPage
import com.ichi2.anki.preferences.PreferencesActivity
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.utils.Destination
import com.ichi2.anki.utils.ext.defaultConfig
import com.ichi2.anki.utils.ext.dismissAllDialogFragments
import com.ichi2.anki.widgets.DeckAdapter
import com.ichi2.testutils.BackendEmulatingOpenConflict
import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.testutils.common.Flaky
import com.ichi2.testutils.common.OS
import com.ichi2.testutils.ext.addBasicNoteWithOp
import com.ichi2.testutils.ext.menu
import com.ichi2.testutils.ext.snackbarText
import com.ichi2.testutils.ext.text
import com.ichi2.testutils.grantWritePermissions
import com.ichi2.testutils.revokeWritePermissions
import com.ichi2.testutils.withWritePermissions
import kotlinx.coroutines.flow.merge
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.runner.RunWith
import org.mockito.Mockito.times
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import timber.log.Timber
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import com.ichi2.anki.common.android.R as CommonR

typealias ContextMenuOption = DeckPickerContextMenuOption

@RunWith(ParameterizedRobolectricTestRunner::class)
class DeckPickerTest : DeckPickerLayoutTest() {
    @Test
    fun `study shortcut on hidden Default shows the empty deck message`() {
        val other = addDeck("Other")
        col.setDeck(listOf(addBasicNote().firstCard().id), other)
        assertEquals(DEFAULT_DECK_ID, col.decks.selected())
        assertThat(col.sched.deckDueTree().find(DEFAULT_DECK_ID), nullValue())

        deckPicker {
            advanceRobolectricLooperUntil { deckPickerBinding.decks.adapter?.itemCount == 1 }
            shadowOf(this).clearNextStartedActivities()
            advanceRobolectricLooperUntil { viewModel.flowOfStudyDeck.subscriptionCount.value > 0 }

            assertTrue(onKeyUp(KeyEvent.KEYCODE_S, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_S)))
            advanceRobolectricLooperUntil { snackbarText == getString(CommonString.empty_deck) }

            assertEquals(DEFAULT_DECK_ID, col.decks.selected())
            assertThat(shadowOf(this).nextStartedActivity, nullValue())
        }
    }

    @Test
    fun `study shortcut on an empty filtered deck opens the finished screen`() {
        addBasicNote()
        val filtered = col.decks.newFiltered("Filtered")

        deckPicker {
            advanceRobolectricLooperUntil { deckPickerBinding.decks.adapter?.itemCount == 2 }
            shadowOf(this).clearNextStartedActivities()
            advanceRobolectricLooperUntil { viewModel.flowOfStudyDeck.subscriptionCount.value > 0 }

            assertTrue(onKeyUp(KeyEvent.KEYCODE_S, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_S)))
            advanceRobolectricLooperUntil { shadowOf(this).peekNextStartedActivity() != null }

            val intent = assertNotNull(shadowOf(this).nextStartedActivity)
            assertEquals(CongratsPage::class.java.name, intent.getStringExtra(SingleFragmentActivity.EXTRA_FRAGMENT_NAME))
            assertEquals(filtered, col.decks.selected())
        }
    }

    @Test
    fun `receiving opExecuted call doesn't crash if ViewModel is not yet initialized`() {
        // Instantiate DeckPicker directly to simulate a state where the object exists,
        // but Android lifecycle callback (onCreate) have not yet executed.
        DeckPicker()

        assertDoesNotThrow { ChangeManager.notifySubscribers(opChanges { studyQueues = true }, null) }
    }

    @Test
    fun limitAppliedAfterReview() {
        val sched = col.sched
        val dconf = col.decks.defaultConfig
        assertNotNull(dconf)
        dconf.new.perDay = 10
        col.decks.save(dconf)
        for (i in 0..10) {
            addBasicNote("Which card is this ?", i.toString())
        }
        // This set a card as current card
        sched.card
        ensureCollectionLoadIsSynchronous()

        deckPicker {
            assertEquals(
                10,
                dueTree!!
                    .children[0]
                    .newCount
                    .toLong(),
            )
        }
    }

    @Test
    fun confirmDeckDeletionDeletesEmptyDeck() {
        val did = addDeck("Hello World")
        assertThat("Deck was added", col.decks.count(), equalTo(2))
        deckPicker {
            viewModel.deleteDeck(did).join()
            assertThat("deck was deleted", col.decks.count(), equalTo(1))
        }
    }

    @Test
    fun `deleting a deck blocks deck selection while collection access is queued`() =
        runTest {
            val deleted = addDeck("Deleted")
            val other = addDeck("Other")
            val selected = col.decks.selected()
            lateinit var deckPicker: DeckPicker
            withDeckPicker(deckCount = 0) { deckPicker = it }
            deckPicker.awaitVisibleDecks(listOf(deleted, other))

            val deletion =
                withQueuedCollectionAccess {
                    deckPicker.deleteDeck(deleted).also {
                        onView(withText("Other")).perform(click())
                    }
                }
            deletion.join()

            assertEquals("a deck was selected while deleting", selected, col.decks.selected())
        }

    @Test
    fun `Delete shortcut requests confirmation only in tablet layout`() {
        val deckName = "Deck to delete"
        val deckId = addDeck(deckName, setAsSelected = true)
        col.setDeck(listOf(addBasicNote().firstCard().id), deckId)

        deckPicker {
            val handled = onKeyUp(KeyEvent.KEYCODE_DEL, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
            if (!fragmented) {
                assertFalse(handled)
                assertTrue(supportFragmentManager.fragments.none { it is DeckPickerConfirmDeleteDeckDialog })
                return@deckPicker
            }

            assertTrue(handled)
            advanceRobolectricLooperUntil {
                supportFragmentManager.fragments.any { it is DeckPickerConfirmDeleteDeckDialog && it.dialog?.isShowing == true }
            }
            val dialog =
                supportFragmentManager.fragments
                    .filterIsInstance<DeckPickerConfirmDeleteDeckDialog>()
                    .single()
                    .requireDialog() as AlertDialog
            assertEquals(getString(CommonString.delete_deck_title), dialog.title)
            assertThat(dialog.message, containsString(deckName))
            assertEquals(deckId, col.decks.byName(deckName)?.id)
            assertEquals(1, col.cardCount())

            dialog.performPositiveClick()
            advanceRobolectricLooperUntil { col.decks.byName(deckName) == null }
            assertEquals(0, col.cardCount())
        }
    }

    @Test
    fun databaseLockedTest() {
        // don't call .onCreate
        val controller = Robolectric.buildActivity(DeckPickerEx::class.java, Intent())
        saveControllerForCleanup(controller)
        val deckPicker = controller.get()
        deckPicker.handleStartupFailure(InitialActivity.StartupFailure.DatabaseLocked)
        assertThat(
            deckPicker.databaseErrorDialog,
            equalTo(DatabaseErrorDialogType.DIALOG_DB_LOCKED),
        )
    }

    /**
     * The 'Database Locked' dialog offers Settings, opened at the Advanced screen, where
     * changing the 'AnkiDroid directory' resolves the conflict (#21051). Without it, the
     * dialog's only button quits the app, contradicting the guidance in its own message.
     */
    @Test
    fun `database locked dialog links to Advanced settings`() {
        val deckPicker = startRegularActivity<DeckPicker>()
        deckPicker.showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_DB_LOCKED)
        deckPicker.supportFragmentManager.executePendingTransactions()
        advanceRobolectricLooper()

        val settingsButton = (ShadowDialog.getLatestDialog() as AlertDialog).getButton(DialogInterface.BUTTON_NEUTRAL)
        assertEquals("Settings", settingsButton.text.toString())

        shadowOf(deckPicker).clearNextStartedActivities()
        settingsButton.performClick()
        advanceRobolectricLooper()

        val settings = shadowOf(deckPicker).nextStartedActivity
        assertNotNull(settings, "Advanced settings should be opened")
        assertEquals(PreferencesActivity::class.qualifiedName, settings.component?.className)
    }

    /** Until the storage setup flow exists (#19552), the user gets recovery options, not a crash */
    @Test
    fun `storage undecided shows load-failure options rather than crashing`() {
        // don't call .onCreate
        val controller = Robolectric.buildActivity(DeckPickerEx::class.java, Intent())
        saveControllerForCleanup(controller)
        val deckPicker = controller.get()
        deckPicker.handleStartupFailure(InitialActivity.StartupFailure.StorageUndecided)
        assertThat(
            deckPicker.databaseErrorDialog,
            equalTo(DatabaseErrorDialogType.DIALOG_LOAD_FAILED),
        )
    }

    @Test
    fun databaseLockedWithPermissionIntegrationTest() {
        AnkiDroidApp.sentExceptionReportHack = false
        try {
            BackendEmulatingOpenConflict.enable()
            InitialActivityWithConflictTest.setupForDatabaseConflict()
            val d =
                super.startActivityNormallyOpenCollectionWithIntent(
                    DeckPickerEx::class.java,
                    Intent(),
                )
            assertThat(
                "A specific dialog for a conflict should be shown",
                d.databaseErrorDialog,
                equalTo(DatabaseErrorDialogType.DIALOG_DB_LOCKED),
            )
            assertThat(
                "No exception reports should be thrown",
                AnkiDroidApp.sentExceptionReportHack,
                equalTo(false),
            )
        } finally {
            BackendEmulatingOpenConflict.disable()
            InitialActivityWithConflictTest.setupForDefault()
        }
    }

    @Test
    @Ignore("Flaky. Try to unflake now we're using coroutines")
    fun databaseLockedNoPermissionIntegrationTest() {
        // no permissions -> grant permissions -> db locked
        try {
            InitialActivityWithConflictTest.setupForDefault()
            BackendEmulatingOpenConflict.enable()

            deckPickerEx {
                // grant permissions
                InitialActivityWithConflictTest.setupForDatabaseConflict()
                onStoragePermissionGranted()
                assertThat(
                    "A specific dialog for a conflict should be shown",
                    databaseErrorDialog,
                    equalTo(DatabaseErrorDialogType.DIALOG_DB_LOCKED),
                )
            }
        } finally {
            BackendEmulatingOpenConflict.disable()
            InitialActivityWithConflictTest.setupForDefault()
        }
    }

    @Test
    fun deckPickerOpensWithHelpMakeAnkiDroidBetterDialog() {
        // Refactor: It would be much better to use a spy - see if we can get this into Robolectric
        try {
            grantWritePermissions()
            BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
            // We don't show it if the user is new.
            targetContext
                .sharedPrefs()
                .edit { putString("lastVersion", "0.1") }
            val d =
                super.startActivityNormallyOpenCollectionWithIntent(
                    DeckPickerEx::class.java,
                    Intent(),
                )
            assertThat(
                "Analytics opt-in should be displayed",
                d.displayedAnalyticsOptIn,
                equalTo(true),
            )
        } finally {
            revokeWritePermissions()
            BackupManagerTestUtilities.reset()
        }
    }

    @Test
    fun doNotShowOptionsMenuWhenCollectionInaccessible() =
        withNullCollection {
            deckPicker {
                viewModel.refreshMenuState()
                assertThat(
                    "Options menu not displayed when collection is inaccessible",
                    viewModel.optionsMenuState,
                    equalTo(null),
                )
            }
        }

    @Test
    fun showOptionsMenuWhenCollectionAccessible() =
        withWritePermissions {
            deckPicker {
                viewModel.refreshMenuState()
                assertThat(
                    "Options menu displayed when collection is accessible",
                    viewModel.optionsMenuState,
                    notNullValue(),
                )
            }
        }

    @Test
    fun onResumeLoadCollectionFailureWithInaccessibleCollection() {
        revokeWritePermissions()
        withNullCollection {
            deckPicker {
                // Neither collection, not its models will be initialized without storage permission

                // assert: Lazy Collection initialization CollectionTask.LoadCollectionComplete fails
                assertFailsWith<Exception> { getColUnsafe }
            }
        }
    }

    @Test
    fun onResumeLoadCollectionSuccessWithAccessibleCollection() =
        withWritePermissions {
            deckPicker {
                assertThat(
                    "Collection initialization ensured by CollectionTask.LoadCollectionComplete",
                    getColUnsafe,
                    notNullValue(),
                )
                assertThat(
                    "Collection Models Loaded",
                    getColUnsafe.notetypes,
                    notNullValue(),
                )
            }
        }

    @Test
    fun `ContextMenu starts expected dialogs when specific options are selected`() =
        deckPicker {
            val didA = addDeck("Deck 1")

            selectContextMenuOption(ContextMenuOption.RENAME_DECK, didA)
            assertDialogTitleEquals("Rename deck")
            dismissAllDialogFragments()

            selectContextMenuOption(ContextMenuOption.CREATE_SUBDECK, didA)
            assertDialogTitleEquals("Create subdeck")
            dismissAllDialogFragments()

            selectContextMenuOption(ContextMenuOption.CUSTOM_STUDY, didA)
            assertDialogTitleEquals("Custom study")
            dismissAllDialogFragments()

//            TODO test code enters in a recursion in BasicItemSelectedListener inside ExportDialog
//            supportFragmentManager.selectContextMenuOption(DeckPickerContextMenuOption.EXPORT_DECK, didA)
//            assertAlertDialogTitleEquals("Export")
//            dismissAllDialogFragments()
        }

    private fun assertDialogTitleEquals(expectedTitle: String) {
        val actualTitle = (ShadowDialog.getLatestDialog() as AlertDialog).title
        Timber.d("titles = \"$actualTitle\", \"$expectedTitle\"")
        assertEquals(expectedTitle, actualTitle)
    }

    private fun DeckPicker.longPressDeck(name: String): View {
        val decks = deckPickerBinding.decks
        val adapter = decks.adapter as DeckAdapter
        // Background list diffs can finish after the initial main looper drain.
        advanceRobolectricLooperUntil(lazyMessage = { "Deck '$name' was not laid out" }) {
            val position = adapter.currentList.indexOfFirst { it.lastDeckNameComponent == name }
            position >= 0 && decks.findViewHolderForAdapterPosition(position) != null
        }
        val deck = adapter.currentList.single { it.lastDeckNameComponent == name }
        val position = adapter.currentList.indexOf(deck)
        decks.findViewHolderForAdapterPosition(position)!!.itemView.performLongClick()
        advanceRobolectricLooperUntil { adapter.currentList[position].isSelected }
        return decks.findViewHolderForAdapterPosition(position)!!.itemView
    }

    @Test
    fun `ContextMenu starts expected activities when specific options are selected`() =
        deckPicker {
            suspend fun DeckPicker.selectContextMenuOptionForActivity(
                option: ContextMenuOption,
                deckId: DeckId,
            ): Intent {
                var result: Any? = null
                merge(viewModel.flowOfDestination, viewModel.flowOfNavigate).test(1.seconds) {
                    selectContextMenuOption(option, deckId)
                    result = awaitItem()
                }
                return when (val emitted = result!!) {
                    is Destination -> emitted.toIntent(this)
                    is com.ichi2.anki.common.destinations.Destination -> AnkiDroidNavigator.toIntent(emitted)
                    else -> error("Unexpected destination type: $emitted")
                }
            }

            val didA = addDeck("Deck 1")
            val didDynamicA = addDynamicDeck("Deck Dynamic 1")

            val noteEditor = selectContextMenuOptionForActivity(DeckPickerContextMenuOption.ADD_CARD, didA)
            assertEquals("com.ichi2.anki.NoteEditorActivity", noteEditor.component!!.className)
            onBackPressedDispatcher.onBackPressed()

            val browser = selectContextMenuOptionForActivity(DeckPickerContextMenuOption.BROWSE_CARDS, didA)
            assertEquals("com.ichi2.anki.CardBrowser", browser.component!!.className)
            onBackPressedDispatcher.onBackPressed()

            // select deck options for a normal deck
            val deckOptionsNormal = selectContextMenuOptionForActivity(DeckPickerContextMenuOption.DECK_OPTIONS, didA)
            assertEquals("com.ichi2.anki.SingleFragmentActivity", deckOptionsNormal.component!!.className)
            onBackPressedDispatcher.onBackPressed()

            // select deck options for a dynamic deck
            val deckOptionsDynamic = selectContextMenuOptionForActivity(DeckPickerContextMenuOption.DECK_OPTIONS, didDynamicA)
            assertEquals("com.ichi2.anki.utils.ConfigAwareSingleFragmentActivity", deckOptionsDynamic.component!!.className)
            onBackPressedDispatcher.onBackPressed()

            Prefs.newReviewRemindersEnabled = true
            val scheduleReminders = selectContextMenuOptionForActivity(DeckPickerContextMenuOption.SCHEDULE_REMINDERS, didA)
            assertEquals("com.ichi2.anki.utils.ConfigAwareSingleFragmentActivity", scheduleReminders.component!!.className)
            onBackPressedDispatcher.onBackPressed()
        }

    @Test
    fun `ContextMenu deletes deck when selecting DELETE_DECK`() =
        deckPicker {
            val didA = addDeck("Deck 1")
            selectContextMenuOption(ContextMenuOption.DELETE_DECK, didA)
            assertThat(getColUnsafe.decks.allNamesAndIds().map { it.id }, not(containsInAnyOrder(didA)))
        }

    @Test
    fun `ContextMenu creates deck shortcut when selecting CREATE_SHORTCUT`() =
        deckPicker {
            val didA = addDeck("Deck 1")
            selectContextMenuOption(ContextMenuOption.CREATE_SHORTCUT, didA)
            // Wait for the shortcut creation to complete
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            assertEquals(
                "Deck 1",
                ShortcutManagerCompat.getShortcuts(this, ShortcutManagerCompat.FLAG_MATCH_PINNED).first().shortLabel,
            )
        }

    @Test
    @Flaky(OS.ALL)
    fun `ContextMenu unburied cards when selecting UNBURY`() =
        deckPicker {
            TimeManager.reset()
            // stop 'next day' code running, which calls 'unbury'
            updateDeckList()
            val deckId = addDeck("Deck 1")
            getColUnsafe.decks.select(deckId)
            getColUnsafe.notetypes.byName("Basic")!!.did = deckId
            val card = addBasicNote("front", "back").firstCard()
            getColUnsafe.sched.buryCards(listOf(card.id))
            updateDeckList()
            advanceRobolectricLooper()
            assertEquals(1, visibleDeckCount)
            assertTrue(getColUnsafe.sched.haveBuried(), "Deck should have buried cards")
            selectContextMenuOption(ContextMenuOption.UNBURY, deckId)
            kotlin.test.assertFalse(getColUnsafe.sched.haveBuried())
        }

    @Test
    fun `ContextMenu testDynRebuildAndEmpty`() {
        val cardIds = (0..3).map { addBasicNote("$it", "").firstCard().id }
        assertTrue(allCardsInSameDeck(cardIds, 1))
        val deckId = addDynamicDeck("Deck 1")
        col.sched.rebuildFilteredDeck(deckId)
        assertTrue(allCardsInSameDeck(cardIds, deckId))

        deckPicker(expectedDeckIds = listOf(deckId)) {
            selectContextMenuOptionAndAwait(ContextMenuOption.CUSTOM_STUDY_EMPTY, deckId) {
                allCardsInSameDeck(cardIds, 1)
            }

            selectContextMenuOptionAndAwait(ContextMenuOption.CUSTOM_STUDY_REBUILD, deckId) {
                allCardsInSameDeck(cardIds, deckId)
            }
        }
    }

    @Test
    fun `rebuilding a filtered deck refreshes the deck list - issue 14285`() =
        deckPicker {
            repeat(4) { addBasicNote("$it", "") }
            val deckId = addDynamicDeck("Filtered", search = "")
            selectContextMenuOption(ContextMenuOption.CUSTOM_STUDY_EMPTY, deckId)
            advanceRobolectricLooperUntil { dueTree?.find(deckId)?.newCount == 0 }

            selectContextMenuOption(ContextMenuOption.CUSTOM_STUDY_REBUILD, deckId)

            advanceRobolectricLooperUntil { dueTree?.find(deckId)?.newCount == 4 }
        }

    private fun allCardsInSameDeck(
        cardIds: List<Long>,
        deckId: DeckId,
    ): Boolean = cardIds.all { col.getCard(it).did == deckId }

    @Test
    fun checkIfReturnsTrueWhenAtLeastOneDeckIsDisplayed() {
        addDeck("Hello World")
        // Reason for using 2 as the number of decks -> This deck + Default deck
        assertThat("Deck added", col.decks.count(), equalTo(2))

        deckPicker {
            assertThat(
                "Deck is being displayed",
                hasAtLeastOneDeckBeingDisplayed(),
                equalTo(true),
            )
        }
    }

    @Test
    fun checkIfReturnsFalseWhenNoDeckIsDisplayed() {
        // Only default deck would be there in the count, hence using the value as 1.
        // Default deck does not get displayed in the DeckPicker if the default deck is empty.
        assertThat("Contains only default deck", col.decks.count(), equalTo(1))

        deckPicker {
            assertThat(
                "No deck is being displayed",
                hasAtLeastOneDeckBeingDisplayed(),
                equalTo(false),
            )
        }
    }

    @Test
    fun `long pressed deck is highlighted on phones`() {
        val initiallySelectedDeck = addDeck("Initially selected")
        addDeck("Long pressed")
        col.decks.select(initiallySelectedDeck)

        deckPicker {
            assumeTrue("Not running on tablet", !fragmented)
            val selectedDeck = longPressDeck("Long pressed")

            assertThat(
                shadowOf(selectedDeck.background).createdFromResId,
                equalTo(CommonR.drawable.item_background_selected),
            )
        }
    }

    @Test
    fun `unbury is usable - Issue 15050`() {
        // We had an issue where 'Unbury' was not visible
        // This was because the deck selection was not changed when a long press occurred

        // one empty deck to be initially selected, one with cards to check 'unbury' status
        val emptyDeck = addDeck("No Cards")
        val deckWithCards = addDeck("With Cards")
        updateDeckConfig(deckWithCards) { new.bury = true }

        // Add a note with 2 cards in deck "With Cards", one of these cards is to be buried
        col.notetypes.byName("Basic (and reversed card)")!!.also { noteType ->
            col.notetypes.save(noteType.apply { did = deckWithCards })
        }
        addBasicAndReversedNote()

        // Answer 'Easy' for one of the cards, burying the other
        col.decks.select(deckWithCards)
        col.sched.deckDueTree() // ? if not called, decks.select(toSelect) un-buries a card
        col.sched.answerCard(col.sched.card!!, Rating.EASY)
        assertThat("the other card is buried", col.sched.card, nullValue())

        // select a deck with no cards
        col.decks.select(emptyDeck)
        assertThat("unbury is not visible: deck has no cards", !col.sched.haveBuried())

        deckPicker {
            assertThat("deck focus is set", viewModel.focusedDeck, equalTo(emptyDeck))

            // ACT: open up the Deck Context Menu
            longPressDeck("With Cards")

            // ASSERT
            assertThat("unbury is visible: one card is buried", col.sched.haveBuried())
            assertThat("deck focus has changed", viewModel.focusedDeck, equalTo(deckWithCards))
        }
    }

    @Test
    fun `undo menu item is updated after undoableOp call`() =
        deckPicker {
            fun DeckPicker.getUndoTitle() = menu().findItem(R.id.action_undo).title.toString()

            fun waitForMenu() = ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

            suspend fun DeckPicker.undo() {
                undoAndShowSnackbar()
                waitForMenu()
            }

            // enqueue two actions, neither of which affect the study queues
            val note = addBasicNoteWithOp()
            note.updateOp { this.fields[0] = "baz" }

            waitForMenu()
            assertThat(getUndoTitle(), containsString("Update Note"))
            undo()
            assertThat(getUndoTitle(), containsString("Add Note"))
        }

    @Test
    fun `On a new startup, the App Intro is displayed`() =
        deckPicker(skipIntroduction = false) {
            val nextIntent = Shadows.shadowOf(this).nextStartedActivity

            assertThat(
                "App Intro should be started on a new startup",
                nextIntent.component?.className,
                equalTo(IntroductionActivity::class.java.name),
            )
        }

    @Suppress("RedundantValueArgument")
    @Test
    fun `On not a new startup, the App Intro is not displayed`() =
        deckPicker(skipIntroduction = true) {
            val nextIntent = Shadows.shadowOf(this).nextStartedActivity

            assertThat(
                "No other activity should be started when not a new startup",
                nextIntent,
                equalTo(null),
            )
        }

    @Test
    fun `rotating while finishing to show the App Intro does not crash`() {
        setIntroductionSlidesShown(false)
        val controller = Robolectric.buildActivity(DeckPicker::class.java, Intent()).create()
        saveControllerForCleanup(controller)
        val deckPicker = controller.get()
        assertTrue(deckPicker.isFinishing, "DeckPicker should finish to show the App Intro")

        val current = deckPicker.resources.configuration
        val rotated =
            Configuration(current).apply {
                orientation = Configuration.ORIENTATION_LANDSCAPE
                screenWidthDp = current.screenHeightDp
                screenHeightDp = current.screenWidthDp
            }
        assertDoesNotThrow { controller.configurationChange(rotated, deckPicker.resources.displayMetrics) }
    }

    @Test
    fun `startup response is cleared after handling so it does not re-run on resume`() =
        deckPicker {
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            assertThat(
                "startup response cleared after handling so it does not re-run on resume",
                viewModel.flowOfStartupResponse.value,
                nullValue(),
            )
        }

    @Test
    fun `menu state refresh tolerates a cleared menu`() =
        deckPicker {
            createMenuJob?.join()
            assertNotNull(viewModel.optionsMenuState)
            val menu = menu()
            // Menu invalidation can clear the menu while refreshMenuState is suspended.
            menu.clear()

            updateMenuFromState(menu)

            assertEquals(0, menu.size)
        }

    @Test
    fun `menu state refresh tolerates a removed sync item`() =
        deckPicker {
            createMenuJob?.join()
            assertNotNull(viewModel.optionsMenuState)
            val menu = menu()
            assertNotNull(menu.findItem(R.id.action_undo))
            menu.removeItem(R.id.action_sync)

            updateMenuFromState(menu)

            assertThat(menu.findItem(R.id.action_sync), nullValue())
        }

    @Test
    fun `fatal error arriving after menu creation blanks the options menu`() =
        deckPickerEx {
            assertThat("menu was built normally on startup", lastCreateOptionsMenuResult, equalTo(true))

            // a slow startup check delivers a fatal error once the menu already exists
            viewModel.flowOfStartupResponse.value =
                DeckPickerViewModel.StartupResponse.FatalError(InitialActivity.StartupFailure.DatabaseLocked)
            advanceRobolectricLooper()
            advanceRobolectricLooper()

            assertThat("the failure was handled", databaseErrorDialog, equalTo(DatabaseErrorDialogType.DIALOG_DB_LOCKED))
            assertThat("the menu is rebuilt and blanked", lastCreateOptionsMenuResult, equalTo(false))
        }

    @Test
    fun `success arriving after menu creation refreshes the options menu`() =
        deckPickerEx {
            CollectionManager.closeCollectionBlocking()
            invalidateOptionsMenu()
            advanceRobolectricLooper()
            createMenuJob?.join()
            assertThat("menu built while the collection was closed", viewModel.optionsMenuState, nullValue())

            CollectionManager.getColUnsafe()
            viewModel.flowOfStartupResponse.value = DeckPickerViewModel.StartupResponse.Success
            advanceRobolectricLooper()
            advanceRobolectricLooper()
            createMenuJob?.join()

            assertThat(
                "the menu is rebuilt once startup succeeds",
                viewModel.optionsMenuState?.syncIcon,
                equalTo(SyncIconState.NotLoggedIn),
            )
        }

    /** Regression test for [#20712](https://github.com/ankidroid/Anki-Android/issues/20712) */
    @Test
    fun `SQLiteDatabaseCorruptException in runCatching shows database error dialog`() =
        deckPickerEx {
            runCatching { throw SQLiteDatabaseCorruptException() }
            assertThat(databaseErrorDialog, equalTo(DatabaseErrorDialogType.DIALOG_LOAD_FAILED))
        }

    /** The backend may raise corruption directly, without conversion to a SQLite exception */
    @Test
    fun `BackendDbCorruptException in runCatching shows database error dialog`() =
        deckPickerEx {
            runCatching { throw BackendDbCorruptException(backendError {}) }
            assertThat(databaseErrorDialog, equalTo(DatabaseErrorDialogType.DIALOG_LOAD_FAILED))
        }

    @Test
    fun `creating a deck selects it`() =
        deckPicker {
            showCreateDeckDialog()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            dialog.input = "My Deck"
            dialog.performPositiveClick()
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

            val newDeckId = col.decks.byName("My Deck")!!.id
            assertThat(
                "the newly created deck should become the current deck",
                col.decks.current().id,
                equalTo(newDeckId),
            )
        }

    enum class CollectionType(
        val assetFile: String,
        private val deckName: String,
    ) {
        SCHEMA_V_16("schema16.anki2", "ThisIsSchema16"),
        SCHEMA_V_250(
            "schema250.anki2",
            "ThisIsSchema250",
        ),
        ;

        fun isCollection(col: com.ichi2.anki.libanki.Collection): Boolean = col.decks.byName(deckName) != null
    }

    internal class DeckPickerEx : DeckPicker() {
        var databaseErrorDialog: DatabaseErrorDialogType? = null
        var displayedAnalyticsOptIn = false
        var optionsMenu: Menu? = null
        var integrityCheckCalled = false

        override fun integrityCheck() {
            integrityCheckCalled = true
        }

        /** result of the last [onCreateOptionsMenu] call: false means the menu was blanked */
        var lastCreateOptionsMenuResult: Boolean? = null

        override fun onCreateOptionsMenu(menu: Menu): Boolean = super.onCreateOptionsMenu(menu).also { lastCreateOptionsMenuResult = it }

        override fun showDatabaseErrorDialog(
            errorDialogType: DatabaseErrorDialogType,
            exceptionData: DatabaseErrorDialog.CustomExceptionData?,
        ) {
            databaseErrorDialog = errorDialogType
        }

        fun onStoragePermissionGranted() {
            onRequestPermissionsResult(
                REQUEST_STORAGE_PERMISSION,
                arrayOf(""),
                intArrayOf(PackageManager.PERMISSION_GRANTED),
            )
        }

        override fun displayAnalyticsOptInDialog() {
            displayedAnalyticsOptIn = true
            super.displayAnalyticsOptInDialog()
        }

        override fun onPrepareOptionsMenu(menu: Menu): Boolean {
            optionsMenu = menu
            return super.onPrepareOptionsMenu(menu)
        }
    }

    @Test
    fun draggingUnsupportedFileShowsImportError() =
        deckPicker {
            val clipData = android.content.ClipData.newRawUri("unsupported", "file:///path/to/image.jpg".toUri())
            val payload =
                ContentInfoCompat
                    .Builder(clipData, ContentInfoCompat.SOURCE_DRAG_AND_DROP)
                    .build()
            ViewCompat.performReceiveContent(findViewById(R.id.pull_to_sync_wrapper), payload)

            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            assertThat(dialog.isShowing, equalTo(true))
            assertThat(dialog.title, equalTo(getString(CommonString.import_title_error)))
        }
}

fun RobolectricTest.setIntroductionSlidesShown(shown: Boolean) {
    getPreferences().edit {
        putBoolean(IntroductionActivity.PREF_INTRODUCTION_SLIDES_SHOWN, shown)
    }
}

/**
 * Launches the deck picker, waiting for the initial deck list after successful startup.
 * When supplied, also waits for [expectedDeckIds] in adapter order before running [function].
 * Prepare collection data before calling this helper.
 */
fun RobolectricTest.deckPicker(
    exposeTestData: Boolean = false,
    skipIntroduction: Boolean = true,
    expectedDeckIds: List<DeckId>? = null,
    function: suspend DeckPicker.() -> Unit,
) = runTest {
    setIntroductionSlidesShown(skipIntroduction)
    val deckPicker =
        startActivityNormallyOpenCollectionWithIntent(
            if (exposeTestData) DeckPickerTest.DeckPickerEx::class.java else DeckPicker::class.java,
            Intent(),
        )
    val viewModel = deckPicker.viewModel
    // Startup can stop at permissions, an error dialog, or the app introduction.
    if (!deckPicker.isFinishing && viewModel.startupJob?.isCompleted == true && viewModel.flowOfStartupResponse.value == null) {
        val expectedCount =
            withCol {
                sched.deckDueTree().filterAndFlattenDisplay(DeckFilters.create(viewModel.deckSearchQuery), decks.selected()).size
            }
        RobolectricTest.advanceRobolectricLooperUntil(
            lazyMessage = { "Expected $expectedCount visible decks, got ${deckPicker.visibleDeckCount}" },
        ) { deckPicker.visibleDeckCount == expectedCount }
    }
    expectedDeckIds?.let { deckPicker.awaitVisibleDecks(it) }
    function(deckPicker)
}

/**
 * Runs [function], providing it access to test-only properties from [DeckPickerTest.DeckPickerEx]
 *
 * @see DeckPickerTest.DeckPickerEx
 */
internal fun RobolectricTest.deckPickerEx(
    skipIntroduction: Boolean = true,
    function: suspend DeckPickerTest.DeckPickerEx.() -> Unit,
) = deckPicker(exposeTestData = true, skipIntroduction = skipIntroduction) {
    function(this as DeckPickerTest.DeckPickerEx)
}
