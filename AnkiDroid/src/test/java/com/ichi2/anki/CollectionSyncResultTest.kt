// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.NetworkType
import anki.backend.backendError
import anki.sync.SyncAuth
import anki.sync.SyncCollectionResponse
import anki.sync.syncCollectionResponse
import com.ichi2.anki.dialogs.SyncErrorDialog
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.utils.ext.DIALOG_FRAGMENT_TAG
import com.ichi2.testutils.ext.snackbarText
import kotlinx.coroutines.runBlocking
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendFactory
import net.ankiweb.rsdroid.exceptions.BackendInterruptedException
import net.ankiweb.rsdroid.exceptions.BackendSyncException
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CollectionSyncResultTest : RobolectricTest() {
    private var required = SyncCollectionResponse.ChangesRequired.NO_CHANGES
    private var syncFailure: Exception? = null

    @Before
    fun prepareSync() {
        getPreferences().edit { putBoolean(getResourceString(R.string.automatic_sync_choice_key), false) }
        Prefs.hkey = "test"
        Prefs.lastSyncTime = 0
        runBlocking { CollectionManager.discardBackend() }
        BackendFactory.setOverride {
            object : Backend() {
                override fun syncCollection(
                    auth: SyncAuth,
                    syncMedia: Boolean,
                ): SyncCollectionResponse {
                    syncFailure?.let { throw it }
                    return syncCollectionResponse { required = this@CollectionSyncResultTest.required }
                }
            }
        }
    }

    @After
    fun resetBackend() {
        BackendFactory.setOverride(null)
    }

    @Test
    fun `successful sync returns after updating the collection and last sync time`() = assertResult(CollectionSyncResult.SUCCESS)

    @Test
    fun `full sync conflict returns with the decision dialog visible`() {
        required = SyncCollectionResponse.ChangesRequired.FULL_SYNC

        assertResult(CollectionSyncResult.CONFLICT) { assertSyncDialogVisible() }
    }

    @Test
    fun `clock error returns failure with the recovery dialog visible`() {
        syncFailure =
            BackendSyncException(backendError { message = CollectionManager.TR.syncClockOff() })

        assertResult(CollectionSyncResult.FAILED) { assertSyncDialogVisible() }
    }

    @Test
    fun `cancelled sync returns after reporting cancellation`() {
        syncFailure =
            BackendInterruptedException(backendError { message = "Sync cancelled" })

        assertResult(CollectionSyncResult.CANCELLED) {
            advanceRobolectricLooperUntil { snackbarText == "Sync cancelled" }
        }
    }

    private fun assertResult(
        expected: CollectionSyncResult,
        checkUi: DeckPicker.() -> Unit = {},
    ) = deckPicker {
        assertEquals(expected, syncAndRefresh(assertNotNull(syncAuth()), null, syncMedia = false, NetworkType.CONNECTED))
        assertTrue(Prefs.lastSyncTime != 0L)
        checkUi()
    }

    private fun DeckPicker.assertSyncDialogVisible() {
        val dialog = assertIs<SyncErrorDialog>(supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG))
        assertTrue(dialog.requireDialog().isShowing)
    }
}
