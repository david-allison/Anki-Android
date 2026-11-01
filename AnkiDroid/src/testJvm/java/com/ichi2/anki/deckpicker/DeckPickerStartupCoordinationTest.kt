// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.deckpicker

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.InitialActivity
import com.ichi2.anki.StoragePermissionSet
import com.ichi2.testutils.JvmTest
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.lessThan
import org.junit.After
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class DeckPickerStartupCoordinationTest : JvmTest() {
    private val viewModels = ViewModelStore()
    private var model: DeckPickerViewModel? = null
    private val viewModel: DeckPickerViewModel
        get() =
            model ?: DeckPickerViewModel().also {
                model = it
                viewModels.put("deck-picker", it)
            }

    @After
    override fun tearDown() {
        try {
            val job = model?.viewModelScope?.coroutineContext?.job
            viewModels.clear()
            job?.let { runBlocking { it.join() } }
        } finally {
            super.tearDown()
        }
    }

    @Test
    fun `handleStartup does not block while a hung sync holds the collection queue`() =
        runTest {
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
                releaseQueue.countDown()
                hungSync.join()
            }
        }

    @Test
    fun `startup loads the deck list once and skips earlier refreshes`() =
        runTest {
            col
            val releaseStartup = CountDownLatch(1)
            try {
                viewModel.flowOfDecksReloaded.test {
                    viewModel.handleStartup(startupEnvironmentHeldUntil(releaseStartup))
                    viewModel.updateDeckList().join()
                    expectNoEvents()

                    releaseStartup.countDown()
                    viewModel.startupJob!!.join()
                    awaitItem()
                    expectNoEvents()
                }
            } finally {
                releaseStartup.countDown()
            }
        }

    @Test
    fun `a refresh after the screen resumed reloads the deck list`() =
        runTest {
            col
            val releaseStartup = CountDownLatch(1)
            try {
                viewModel.flowOfDecksReloaded.test {
                    viewModel.handleStartup(startupEnvironmentHeldUntil(releaseStartup))
                    viewModel.onScreenResumed()

                    releaseStartup.countDown()
                    viewModel.startupJob!!.join()
                    awaitItem()

                    viewModel.updateDeckList().join()
                    awaitItem()
                }
            } finally {
                releaseStartup.countDown()
            }
        }

    private fun startupEnvironmentHeldUntil(release: CountDownLatch) =
        object : DeckPickerViewModel.AnkiDroidEnvironment by grantedPermissionsEnvironment {
            override fun getStartupFailure(): InitialActivity.StartupFailure? {
                check(release.await(WATCHDOG_TIMEOUT)) { "startup was not released" }
                return grantedPermissionsEnvironment.getStartupFailure()
            }
        }

    private val grantedPermissionsEnvironment =
        object : DeckPickerViewModel.AnkiDroidEnvironment {
            override fun hasRequiredPermissions() = true

            override val requiredPermissions: StoragePermissionSet
                get() = error("unused: permissions are granted")

            override fun decideStorageIfUndecided() = Unit

            override fun getStartupFailure(): InitialActivity.StartupFailure? {
                CollectionManager.getColUnsafe()
                return null
            }

            override fun configureRenderingMode() = Unit
        }
}

/** how long the watchdog lets a blocked [DeckPickerViewModel.handleStartup] hold the test before freeing the queue */
private val WATCHDOG_TIMEOUT = 2.seconds

private fun CountDownLatch.await(timeout: Duration): Boolean = await(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
