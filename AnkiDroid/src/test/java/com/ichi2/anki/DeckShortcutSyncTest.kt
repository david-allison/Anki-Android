// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.core.content.edit
import anki.backend.backendError
import anki.sync.SyncAuth
import anki.sync.SyncCollectionResponse
import anki.sync.SyncStatusResponse
import anki.sync.syncCollectionResponse
import anki.sync.syncStatusResponse
import com.ichi2.anki.IntentHandler.Companion.EXTRA_DECK_ID
import com.ichi2.anki.dialogs.SyncErrorDialog
import com.ichi2.anki.libanki.Consts.DEFAULT_DECK_ID
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.enums.ShouldFetchMedia
import com.ichi2.anki.utils.ext.DIALOG_FRAGMENT_TAG
import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.utils.NetworkUtils
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.exceptions.BackendInterruptedException
import net.ankiweb.rsdroid.exceptions.BackendSyncException
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowToast
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(ParameterizedRobolectricTestRunner::class)
class DeckShortcutSyncTest : RobolectricTest() {
    @ParameterizedRobolectricTestRunner.Parameter
    @JvmField
    var newStudyScreen = false

    private lateinit var backend: Backend
    private var deckId: DeckId = 0

    @Before
    fun prepareShortcut() {
        setIntroductionSlidesShown(true)
        InitialActivity.setUpgradedToLatestVersion(getPreferences())
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        getPreferences().edit {
            putBoolean("backupPromptDisabled", true)
            putBoolean(getResourceString(R.string.automatic_sync_choice_key), true)
        }
        Prefs.isNewStudyScreenEnabled = newStudyScreen
        Prefs.hkey = "test"
        Prefs.lastSyncTime = 0
        Prefs.allowSyncOnMeteredConnections = true
        Prefs.putEnum(R.string.sync_fetch_media_key, ShouldFetchMedia.NEVER)
        deckId = addDeck("Shortcut deck")
        col.setDeck(listOf(addBasicNote().firstCard().id), deckId)
        col.decks.select(DEFAULT_DECK_ID)
        backend = col.backend
        mockkObject(NetworkUtils, backend)
        every { NetworkUtils.isOnline } returns true
        every { backend.syncStatus(any<SyncAuth>()) } returns
            syncStatusResponse { required = SyncStatusResponse.Required.NORMAL_SYNC }
        every { backend.syncCollection(any<SyncAuth>(), any()) } returns
            syncCollectionResponse { required = SyncCollectionResponse.ChangesRequired.NO_CHANGES }
    }

    @After
    fun resetShortcut() {
        unmockkObject(NetworkUtils, backend)
        BackupManagerTestUtilities.reset()
    }

    @Test
    fun `shortcut routes only to the deck picker without selecting a deck`() {
        val handler =
            startActivityNormallyOpenCollectionWithIntent(
                IntentHandler::class.java,
                IntentHandler.getReviewDeckIntent(targetContext, deckId),
            )
        val shadow = shadowOf(handler)
        val destination = assertNotNull(shadow.nextStartedActivity)

        assertEquals(DeckPicker::class.java.name, destination.component?.className)
        assertEquals(deckId, destination.getLongExtra(EXTRA_DECK_ID, 0))
        assertNull(shadow.nextStartedActivity, "The entry point must not also start the reviewer")
        assertEquals(DEFAULT_DECK_ID, col.decks.selected())
    }

    @Test
    fun `shortcut opens the requested deck when automatic sync is disabled`() {
        getPreferences().edit { putBoolean(getResourceString(R.string.automatic_sync_choice_key), false) }

        assertReviewerOpened(launchDeckPicker())
    }

    @Test
    fun `shortcut waits for both the status request and collection sync`() {
        val statusStarted = CountDownLatch(1)
        val releaseStatus = CountDownLatch(1)
        val syncStarted = CountDownLatch(1)
        val releaseSync = CountDownLatch(1)
        every { backend.syncStatus(any<SyncAuth>()) } answers {
            statusStarted.countDown()
            check(releaseStatus.await(20, TimeUnit.SECONDS))
            syncStatusResponse { required = SyncStatusResponse.Required.NORMAL_SYNC }
        }
        every { backend.syncCollection(any<SyncAuth>(), any()) } answers {
            syncStarted.countDown()
            check(releaseSync.await(20, TimeUnit.SECONDS))
            syncCollectionResponse { required = SyncCollectionResponse.ChangesRequired.NO_CHANGES }
        }

        // Blocking backend calls need the production queue, rather than the test thread's reentrant lock.
        val previousDispatcher = CollectionManager.setTestDispatcher(Dispatchers.IO.limitedParallelism(1), useReentrantLock = false)
        try {
            val activity = launchDeckPicker()
            advanceRobolectricLooperUntil { statusStarted.count == 0L }
            assertNull(shadowOf(activity).peekNextStartedActivity())

            releaseStatus.countDown()
            advanceRobolectricLooperUntil { syncStarted.count == 0L }
            assertNull(shadowOf(activity).peekNextStartedActivity())

            releaseSync.countDown()
            assertReviewerOpened(activity)
        } finally {
            releaseStatus.countDown()
            releaseSync.countDown()
            CollectionManager.setTestDispatcher(previousDispatcher)
        }
    }

    @Test
    fun `shortcut leaves a full sync conflict visible before reviewing`() {
        every { backend.syncCollection(any<SyncAuth>(), any()) } returns
            syncCollectionResponse { required = SyncCollectionResponse.ChangesRequired.FULL_SYNC }

        val controller = launchDeckPickerController()
        val activity = controller.get()
        advanceRobolectricLooperUntil { Prefs.lastSyncTime != 0L }

        val dialog = activity.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG) as? SyncErrorDialog
        assertTrue(assertNotNull(dialog).requireDialog().isShowing)
        assertNull(shadowOf(activity).peekNextStartedActivity())
        assertEquals(DEFAULT_DECK_ID, col.decks.selected())
        assertFalse(activity.intent.hasExtra(EXTRA_DECK_ID))

        controller.recreate()
        controller.get().viewModel.awaitStartup()
        assertNull(shadowOf(controller.get()).peekNextStartedActivity(), "Recreation must not bypass the conflict")
    }

    @Test
    fun `shortcut stays in the deck picker when sync is cancelled`() {
        every { backend.syncCollection(any<SyncAuth>(), any()) } throws
            BackendInterruptedException(backendError { message = "Sync cancelled" })

        val activity = launchDeckPicker()
        advanceRobolectricLooperUntil { Prefs.lastSyncTime != 0L }

        assertNull(shadowOf(activity).peekNextStartedActivity())
        assertFalse(activity.intent.hasExtra(EXTRA_DECK_ID))
    }

    @Test
    fun `shortcut keeps sync errors visible`() {
        every { backend.syncCollection(any<SyncAuth>(), any()) } throws
            BackendSyncException(backendError { message = CollectionManager.TR.syncClockOff() })

        val activity = launchDeckPicker()
        advanceRobolectricLooperUntil { Prefs.lastSyncTime != 0L }

        val dialog = activity.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG) as? SyncErrorDialog
        assertTrue(assertNotNull(dialog).requireDialog().isShowing)
        assertNull(shadowOf(activity).peekNextStartedActivity())
        assertFalse(activity.intent.hasExtra(EXTRA_DECK_ID))
    }

    @Test
    fun `shortcut opens after a status check with no collection changes`() {
        every { backend.syncStatus(any<SyncAuth>()) } returns SyncStatusResponse.getDefaultInstance()

        assertReviewerOpened(launchDeckPicker())
    }

    @Test
    fun `shortcut does not select a deck removed by sync`() {
        val collection = col
        every { backend.syncCollection(any<SyncAuth>(), any()) } answers {
            collection.decks.remove(listOf(deckId))
            syncCollectionResponse { required = SyncCollectionResponse.ChangesRequired.NO_CHANGES }
        }

        val activity = launchDeckPicker()
        advanceRobolectricLooperUntil { Prefs.lastSyncTime != 0L }

        assertNull(shadowOf(activity).peekNextStartedActivity())
        assertFalse(activity.intent.hasExtra(EXTRA_DECK_ID))
        assertEquals(getResourceString(CommonString.deck_shortcut_doesnt_exist), ShadowToast.getTextOfLatestToast())
    }

    private fun launchDeckPicker() = launchDeckPickerController().get()

    private fun launchDeckPickerController(): ActivityController<DeckPicker> =
        startActivityControllerNormallyOpenCollectionWithIntent(
            DeckPicker::class.java,
            DeckPicker.getIntent(targetContext).putExtra(EXTRA_DECK_ID, deckId),
        )

    private fun assertReviewerOpened(activity: DeckPicker) {
        val shadow = shadowOf(activity)
        advanceRobolectricLooperUntil { shadow.peekNextStartedActivity() != null }
        assertEquals(Reviewer.getIntent(activity).component, shadow.nextStartedActivity.component)
        assertEquals(deckId, col.decks.selected())
        assertFalse(activity.intent.hasExtra(EXTRA_DECK_ID), "The shortcut must be consumed before the next resume")
        assertNull(shadow.nextStartedActivity)
    }

    companion object {
        @ParameterizedRobolectricTestRunner.Parameters(name = "new study screen = {0}")
        @JvmStatic
        fun configurations() = listOf(false, true)
    }
}
