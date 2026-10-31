// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.ui.windows.managespace

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.ichi2.anki.common.progress.ViewModelProgress
import com.ichi2.testutils.OnDiskJvmTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ManageSpaceViewModelTest : OnDiskJvmTest() {
    @Test
    fun `deleteMediaFiles reports progress`() =
        runProgressTest {
            assertProgressAround(ManageSpaceProgress.DELETING_MEDIA) { deleteMediaFiles(emptyList()) }
        }

    @Test
    fun `deleteBackups reports progress`() =
        runProgressTest {
            assertProgressAround(ManageSpaceProgress.DELETING_BACKUPS) { deleteBackups(emptyList()) }
        }

    @Test
    fun `deleteCollection reports progress`() =
        runProgressTest {
            assertProgressAround(ManageSpaceProgress.DELETING_COLLECTION) { deleteCollection() }
        }

    @Test
    fun `application storage supplies the total size and receives clear requests`() {
        val storage = TestApplicationStorage()
        runProgressTest(storage) {
            val size = flowOfDeleteEverythingSize.first { it !is Size.Calculating }
            assertEquals(42L, assertIs<Size.Bytes>(size).totalSize)
            deleteEverything()
            assertEquals(1, storage.clearRequests)
        }
    }

    private fun runProgressTest(
        storage: ApplicationStorage = TestApplicationStorage(),
        testBody: suspend ManageSpaceViewModel.() -> Unit,
    ) = runTest {
        col
        val store = ViewModelStore()
        val model = ManageSpaceViewModel(storage).also { store.put("manageSpace", it) }
        val modelJob = model.viewModelScope.coroutineContext.job
        try {
            model.assertNoWorkRunning()
            model.testBody()
            model.assertNoWorkRunning()
        } finally {
            store.clear()
            modelJob.join()
        }
    }

    private fun ManageSpaceViewModel.assertNoWorkRunning() {
        val running =
            viewModelScope.coroutineContext.job.children
                .filter { it.isActive }
                .toList()
        assertTrue(running.isEmpty(), "ViewModel work outlives the test dispatcher: $running")
    }

    private suspend fun ManageSpaceViewModel.assertProgressAround(
        message: ManageSpaceProgress,
        op: suspend () -> Unit,
    ) {
        progressManager.progress.test {
            assertIs<ViewModelProgress.Idle>(awaitItem())
            op()
            val active = assertIs<ViewModelProgress.Active<ManageSpaceProgress>>(awaitItem())
            assertEquals(message, active.message)
            assertIs<ViewModelProgress.Idle>(awaitItem())
            expectNoEvents()
        }
    }
}

private class TestApplicationStorage : ApplicationStorage {
    var clearRequests = 0

    override suspend fun sizeInBytes(): Long = 42

    override fun clear() {
        clearRequests++
    }
}
