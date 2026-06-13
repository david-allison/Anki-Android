// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.exception.StorageNotConfiguredException
import com.ichi2.anki.storage.StorageDecision
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Proves the storage-decision gate in [CollectionManager.ensureOpenInner] is wired up.
 * [CollectionHelper.storageDecision] is [StorageDecision.Decided] once the user has chosen
 * where the collection is stored ([CollectionHelper.PREF_COLLECTION_PATH] is set); tests force
 * [StorageDecision.Undecided] via the test override.
 */
@RunWith(AndroidJUnit4::class)
class StorageDecisionGateTest : RobolectricTest() {
    @After
    fun resetDirectoryOverride() {
        CollectionHelper.ankiDroidDirectoryOverride = null
    }

    @Test
    fun `storage decision is decided when the collection path is set`() {
        targetContext.sharedPrefs().edit { putString(CollectionHelper.PREF_COLLECTION_PATH, "/a/collection/path") }
        assertEquals(StorageDecision.Decided, CollectionHelper.storageDecision())
    }

    @Test
    fun `storage decision is undecided when the collection path is unset`() {
        targetContext.sharedPrefs().edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }
        assertEquals(StorageDecision.Undecided, CollectionHelper.storageDecision())
    }

    @Test
    fun `storage decision is decided when a directory override is active`() {
        targetContext.sharedPrefs().edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }
        CollectionHelper.ankiDroidDirectoryOverride = File("/an/override")
        assertEquals(StorageDecision.Decided, CollectionHelper.storageDecision())
    }

    @Test
    fun `opening the collection throws when storage is undecided`() {
        CollectionHelper.storageDecisionTestOverride = StorageDecision.Undecided
        try {
            assertFailsWith<StorageNotConfiguredException> { CollectionManager.getColUnsafe() }
        } finally {
            CollectionHelper.storageDecisionTestOverride = null
        }
    }

    /** No collection access should be attempted: a crash report would otherwise be generated */
    @Test
    fun `startup failure is StorageUndecided when storage is undecided`() {
        CollectionHelper.storageDecisionTestOverride = StorageDecision.Undecided
        try {
            val failure = InitialActivity.getStartupFailureType { true }
            assertEquals(InitialActivity.StartupFailure.StorageUndecided, failure)
        } finally {
            CollectionHelper.storageDecisionTestOverride = null
        }
    }
}
