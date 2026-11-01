// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.deckpicker

import android.annotation.SuppressLint
import androidx.core.content.edit
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.collection.OpChanges
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.InitialActivity
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.StoragePermissionSet
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.testutils.publicCollectionPath
import com.ichi2.testutils.rules.BackendOverrideRule
import com.ichi2.testutils.withAllFilesAccess
import com.ichi2.testutils.withManageExternalStorageInManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import net.ankiweb.rsdroid.Backend
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.assertEquals

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

            override fun decideStorageIfUndecided() {
                check(hasRequiredPermissions()) { "the storage decision requires granted permissions" }
                InitialActivity.decideStorageIfUndecided(targetContext)
            }

            override fun getStartupFailure() =
                InitialActivity.getStartupFailureType(targetContext.sharedPrefs()) {
                    CollectionHelper.isCurrentAnkiDroidDirAccessible(targetContext)
                }

            override fun configureRenderingMode() = com.ichi2.anki.configureRenderingMode()
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
}
