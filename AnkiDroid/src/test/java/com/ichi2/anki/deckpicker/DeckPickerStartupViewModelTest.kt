// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.deckpicker

import android.annotation.SuppressLint
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.collection.OpChanges
import app.cash.turbine.test
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.InitialActivity
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.StoragePermissionSet
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.common.storage.StorageDecision
import com.ichi2.testutils.publicCollectionPath
import com.ichi2.testutils.rules.BackendOverrideRule
import com.ichi2.testutils.withAllFilesAccess
import com.ichi2.testutils.withManageExternalStorageInManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import net.ankiweb.rsdroid.Backend
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.lessThan
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

@RunWith(AndroidJUnit4::class)
class DeckPickerStartupViewModelTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    private val viewModels = ViewModelStore()
    private val viewModel by lazy { DeckPickerViewModel().also { viewModels.put("deck-picker", it) } }

    @After
    override fun tearDown() {
        try {
            viewModels.clear()
        } finally {
            super.tearDown()
        }
    }

    @Test // Issue 13574: the storage decision may be deferred until permissions are granted
    @SuppressLint("NewApi") // EXTERNAL_MANAGER requires R: Robolectric runs at API 35
    fun `startup decides a collection path once permissions are granted`() =
        runTest {
            targetContext.sharedPrefs().edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }

            withManageExternalStorageInManifest {
                withAllFilesAccess {
                    viewModel.handleStartup(externalManagerEnvironment())
                    advanceRobolectricLooperUntil { viewModel.startupJob?.isCompleted == true }
                }
            }

            assertEquals(publicCollectionPath, targetContext.sharedPrefs().getString(CollectionHelper.PREF_COLLECTION_PATH, null))
            assertEquals(DeckPickerViewModel.StartupResponse.Success, viewModel.flowOfStartupResponse.value)
        }

    @Test // Issue 13574: the storage decision must not be made before the user grants or skips
    @SuppressLint("NewApi") // EXTERNAL_MANAGER requires R: Robolectric runs at API 35
    fun `startup requests permissions without deciding a collection path`() {
        targetContext.sharedPrefs().edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }

        withManageExternalStorageInManifest { viewModel.handleStartup(externalManagerEnvironment()) }

        assertEquals(
            DeckPickerViewModel.StartupResponse.RequestPermissions(StoragePermissionSet.EXTERNAL_MANAGER),
            viewModel.flowOfStartupResponse.value,
        )
        assertThat(
            "no collection path is decided before permissions are granted",
            !targetContext.sharedPrefs().contains(CollectionHelper.PREF_COLLECTION_PATH),
        )
    }

    /**
     * `DeckPicker`'s environment on a build declaring `MANAGE_EXTERNAL_STORAGE`, on Android 11+.
     *
     * Not `@Config(sdk = R)`: startup opens the backend, which is unavailable in Robolectric's
     * per-SDK sandbox. The default SDK satisfies `EXTERNAL_MANAGER`'s API requirement.
     */
    @SuppressLint("NewApi") // EXTERNAL_MANAGER requires R: Robolectric runs at API 35
    private fun externalManagerEnvironment() =
        object : DeckPickerViewModel.AnkiDroidEnvironment {
            override val requiredPermissions = StoragePermissionSet.EXTERNAL_MANAGER

            override fun hasRequiredPermissions() = requiredPermissions.hasRequiredPermissions(targetContext)

            override val preferences: SharedPreferences
                get() = targetContext.sharedPrefs()

            override fun decideStorageIfUndecided() {
                check(hasRequiredPermissions()) { "the storage decision requires granted permissions" }
                InitialActivity.decideStorageIfUndecided(targetContext)
            }

            override fun initializeAnkiDroidFolder(): Boolean = CollectionHelper.isCurrentAnkiDroidDirAccessible(targetContext)
        }

    @Test
    fun `handleStartup does not block while a hung sync holds the collection queue`() {
        col
        val queueHeld = CountDownLatch(1)
        val releaseQueue = CountDownLatch(1)
        // a sync against an unresponsive server runs `withCol { syncCollection(...) }`,
        // holding the collection queue for the whole network call
        val hungSync =
            thread(name = "hung-sync") {
                runBlocking {
                    withCol {
                        queueHeld.countDown()
                        releaseQueue.await()
                    }
                }
            }
        try {
            // in-memory tests don't set a collection path, so force the storage gate open;
            // otherwise getStartupFailureType returns StorageUndecided before reaching the
            // getColUnsafe() call that waits on the collection queue
            CollectionHelper.storageDecisionTestOverride = StorageDecision.Decided

            assertThat("sync is holding the collection queue", queueHeld.await(5.seconds), equalTo(true))

            // if handleStartup blocks on the queue, free it after a delay so the test fails with a message instead of hanging
            thread(name = "watchdog") {
                if (!releaseQueue.await(WATCHDOG_TIMEOUT)) {
                    releaseQueue.countDown()
                }
            }

            val elapsed = measureTime { viewModel.handleStartup(grantedPermissionsEnvironment) }

            assertThat(
                "handleStartup waited $elapsed for the collection queue. On a device this ANRs when DeckPicker is recreated while a sync is stuck on an unresponsive server",
                elapsed,
                lessThan(WATCHDOG_TIMEOUT),
            )
        } finally {
            CollectionHelper.storageDecisionTestOverride = null
            releaseQueue.countDown()
            hungSync.join()
        }
    }

    @Test
    fun `startup loads the deck list once and skips earlier refreshes`() =
        runTest {
            col
            CollectionHelper.storageDecisionTestOverride = StorageDecision.Decided
            val releaseStartup = CountDownLatch(1)
            try {
                viewModel.flowOfDecksReloaded.test {
                    viewModel.handleStartup(startupEnvironmentHeldUntil(releaseStartup))
                    viewModel.updateDeckList().join()
                    expectNoEvents()

                    releaseStartup.countDown()
                    advanceRobolectricLooperUntil { viewModel.startupJob?.isCompleted == true }
                    awaitItem()
                    expectNoEvents()
                }
            } finally {
                CollectionHelper.storageDecisionTestOverride = null
                releaseStartup.countDown()
            }
        }

    @Test
    fun `a refresh after the screen resumed reloads the deck list`() =
        runTest {
            col
            CollectionHelper.storageDecisionTestOverride = StorageDecision.Decided
            val releaseStartup = CountDownLatch(1)
            try {
                viewModel.flowOfDecksReloaded.test {
                    viewModel.handleStartup(startupEnvironmentHeldUntil(releaseStartup))
                    viewModel.onScreenResumed()

                    releaseStartup.countDown()
                    advanceRobolectricLooperUntil { viewModel.startupJob?.isCompleted == true }
                    awaitItem()

                    viewModel.updateDeckList().join()
                    awaitItem()
                }
            } finally {
                CollectionHelper.storageDecisionTestOverride = null
                releaseStartup.countDown()
            }
        }

    @Test
    fun `emptying a filtered deck empties it even if another deck is selected meanwhile`() {
        val selectOther = AtomicBoolean(false)
        val emptied = AtomicLong(0)
        var filtered = 0L
        var other = 0L
        var selection: Job? = null
        backendOverride.replaceWith {
            object : Backend() {
                override fun setCurrentDeck(did: Long): OpChanges {
                    val changes = super.setCurrentDeck(did)
                    if (did == filtered && selectOther.compareAndSet(true, false)) selection = viewModel.selectDeck(other)
                    return changes
                }

                override fun emptyFilteredDeck(did: Long): OpChanges {
                    emptied.set(did)
                    return super.emptyFilteredDeck(did)
                }
            }
        }
        filtered = addDynamicDeck("Filtered")
        other = addDeck("Other")

        withCollectionThread {
            selectOther.set(true)
            val empty = viewModel.emptyFilteredDeck(filtered)
            advanceRobolectricLooperUntil { empty.isCompleted && selection?.isCompleted == true }
        }

        assertEquals(filtered, emptied.get())
    }

    private fun withCollectionThread(block: () -> Unit) {
        val previousQueue = CollectionManager.setTestDispatcher(Dispatchers.IO.limitedParallelism(1), useReentrantLock = false)
        try {
            block()
        } finally {
            CollectionManager.setTestDispatcher(previousQueue)
        }
    }

    private fun startupEnvironmentHeldUntil(release: CountDownLatch) =
        object : DeckPickerViewModel.AnkiDroidEnvironment by grantedPermissionsEnvironment {
            override val preferences: SharedPreferences
                get() {
                    release.await(WATCHDOG_TIMEOUT)
                    return grantedPermissionsEnvironment.preferences
                }
        }

    private val grantedPermissionsEnvironment =
        object : DeckPickerViewModel.AnkiDroidEnvironment {
            override fun hasRequiredPermissions() = true

            override val requiredPermissions: StoragePermissionSet
                get() = error("unused: permissions are granted")

            override val preferences: SharedPreferences
                get() = getPreferences()

            override fun decideStorageIfUndecided() = Unit

            override fun initializeAnkiDroidFolder() = true
        }
}

/** how long the watchdog lets a blocked [DeckPickerViewModel.handleStartup] hold the test before freeing the queue */
private val WATCHDOG_TIMEOUT = 2.seconds

private fun CountDownLatch.await(timeout: Duration): Boolean = await(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
