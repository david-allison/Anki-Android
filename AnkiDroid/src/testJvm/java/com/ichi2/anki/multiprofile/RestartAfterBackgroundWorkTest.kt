// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.multiprofile

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RestartAfterBackgroundWorkTest {
    private val syncLock = Mutex()
    private val mediaSyncLock = Mutex()
    private val backupLock = Mutex()
    private val backgroundWorkLocks get() = listOf(syncLock, mediaSyncLock, backupLock)
    private val events = mutableListOf<String>()

    private suspend fun restart() =
        restartAfterBackgroundWork(
            syncLock = syncLock,
            mediaSyncLock = mediaSyncLock,
            backupLock = backupLock,
            cancelSyncWork = { events.add("cancel sync") },
            closeCollection = { events.add("close collection") },
            restart = { events.add("restart") },
        )

    @Test
    fun `a cancelled restart releases only the locks it took`() =
        runTest {
            backupLock.lock()
            val restart = launch { restart() }
            advanceUntilIdle()

            restart.cancelAndJoin()

            assertFalse(syncLock.isLocked, "a cancelled restart must not leave syncing blocked")
            assertFalse(mediaSyncLock.isLocked, "a cancelled restart must not leave media syncing blocked")
            assertTrue(backupLock.isLocked, "the backup that is still writing holds it")
            assertEquals(listOf("cancel sync"), events)
        }

    @Test
    fun `restart keeps the locks until the process is killed`() =
        runTest {
            restart()

            backgroundWorkLocks.forEach { assertTrue(it.isLocked, "work must not start before ProcessPhoenix kills the process") }
            assertEquals(listOf("cancel sync", "close collection", "restart"), events)
        }

    private suspend fun TestScope.assertRestartWaitsFor(
        lock: Mutex,
        why: String,
    ) {
        lock.lock()
        val restart = launch { restart() }

        advanceUntilIdle()
        assertFalse("restart" in events, why)
        assertFalse("close collection" in events, "the active background work still needs its collection")

        lock.unlock()
        restart.join()
        assertEquals(listOf("cancel sync", "close collection", "restart"), events)
    }

    @Test
    fun `restart waits for a backup that is still writing`() =
        runTest { assertRestartWaitsFor(backupLock, "killing the process mid-write can leave a truncated backup") }

    @Test
    fun `restart waits for a sync that started after the checks passed`() =
        runTest { assertRestartWaitsFor(syncLock, "a sync can start between the checks and the restart") }

    @Test
    fun `restart waits for a media sync that started after the checks passed`() =
        runTest { assertRestartWaitsFor(mediaSyncLock, "a media sync can start between the checks and the restart") }

    @Test
    fun `only the ProcessPhoenix process is recognised as it`() {
        assertTrue(isPhoenixProcessName("com.ichi2.anki:phoenix"))
        assertFalse(isPhoenixProcessName("com.ichi2.anki"))
        assertFalse(isPhoenixProcessName("com.ichi2.anki:acra"))
        assertFalse(isPhoenixProcessName(null))
    }
}
