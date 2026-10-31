// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.multiprofile

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.core.content.IntentCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.IntentHandler
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.backupLock
import com.ichi2.anki.worker.UniqueWorkNames
import com.ichi2.anki.worker.mediaSyncLock
import com.ichi2.anki.worker.syncLock
import com.jakewharton.processphoenix.PhoenixActivity
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AppRestartTest : RobolectricTest() {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val backgroundWorkLocks get() = listOf(syncLock, mediaSyncLock, backupLock)

    @After
    override fun tearDown() {
        backgroundWorkLocks.filter { it.isLocked }.forEach { it.unlock() }
        super.tearDown()
    }

    private fun startedActivities() = shadowOf(context as Application)

    /** The intent ProcessPhoenix relaunches once it has killed this process. */
    private fun relaunchIntent(): Intent? {
        val phoenix = startedActivities().nextStartedActivity ?: return null
        assertEquals(PhoenixActivity::class.java.name, phoenix.component?.className)
        return IntentCompat.getParcelableArrayListExtra(phoenix, "phoenix_restart_intents", Intent::class.java)?.single()
    }

    @Test
    fun `restart relaunches the launcher in a cleared task through ProcessPhoenix`() =
        runTest {
            safeRestartApp(context)

            val intent = assertNotNull(relaunchIntent(), "ProcessPhoenix must be handed the relaunch")
            assertEquals(IntentHandler::class.java.name, intent.component?.className)
            assertTrue(intent.categories.contains(Intent.CATEGORY_LAUNCHER))
            assertTrue(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK != 0, "the old profile's activities must not survive")
        }

    @Test
    fun `restart closes the collection before handing over to ProcessPhoenix`() =
        runTest {
            CollectionManager.ensureOpen()

            safeRestartApp(context)

            assertFalse(CollectionManager.isOpenUnsafe(), "the process is killed next, and a write in flight could corrupt it")
        }

    @Test
    fun `restart cancels sync work queued for the profile being left`() =
        runTest {
            context.enqueueWaiting(UniqueWorkNames.SYNC)
            context.enqueueWaiting(UniqueWorkNames.SYNC_MEDIA)

            safeRestartApp(context)

            assertEquals(WorkInfo.State.CANCELLED, context.workState(UniqueWorkNames.SYNC), "it carries this profile's AnkiWeb key")
            assertEquals(WorkInfo.State.CANCELLED, context.workState(UniqueWorkNames.SYNC_MEDIA), "it carries this profile's AnkiWeb key")
        }

    @Test
    fun `restart cancels a media sync enqueued by a sync that is still finishing`() =
        runTest {
            syncLock.lock()
            val restart = launch { safeRestartApp(context) }
            advanceUntilIdle()

            // SyncWorker enqueues the media sync before it releases syncLock
            context.enqueueWaiting(UniqueWorkNames.SYNC_MEDIA)

            syncLock.unlock()
            restart.join()

            assertEquals(
                WorkInfo.State.CANCELLED,
                context.workState(UniqueWorkNames.SYNC_MEDIA),
                "it carries this profile's AnkiWeb key",
            )
        }

    @Test
    fun `restart cancels a media sync instead of waiting for it to finish`() =
        runTest {
            mediaSyncLock.lock()
            context.enqueueWaiting(UniqueWorkNames.SYNC_MEDIA)
            val restart = launch { safeRestartApp(context) }
            advanceUntilIdle()

            assertEquals(
                WorkInfo.State.CANCELLED,
                context.workState(UniqueWorkNames.SYNC_MEDIA),
                "otherwise the restart sits through a media sync it is about to cancel",
            )

            mediaSyncLock.unlock()
            restart.join()
        }

    @Test
    fun `restart releases the locks if it cannot hand over to ProcessPhoenix`() =
        runTest {
            val refusesActivities =
                object : ContextWrapper(context) {
                    override fun startActivity(intent: Intent) = throw IllegalStateException("cannot start activities")
                }

            assertFailsWith<IllegalStateException> { safeRestartApp(refusesActivities) }

            backgroundWorkLocks.forEach { assertFalse(it.isLocked, "a failed restart must not leave sync and backup blocked") }
        }
}
