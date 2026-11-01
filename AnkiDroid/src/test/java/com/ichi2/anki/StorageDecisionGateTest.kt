// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.common.storage.StorageDecision
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/** Checks the real application startup path before a collection directory has been chosen. */
@RunWith(AndroidJUnit4::class)
class StorageDecisionGateTest : RobolectricTest() {
    @After
    fun resetOverride() {
        CollectionHelper.storageDecisionTestOverride = null
    }

    /** No collection access should be attempted: a crash report would otherwise be generated. */
    @Test
    fun `startup failure is StorageUndecided when storage is undecided`() {
        CollectionHelper.storageDecisionTestOverride = StorageDecision.Undecided
        val failure = InitialActivity.getStartupFailureType(targetContext.sharedPrefs()) { true }
        assertEquals(InitialActivity.StartupFailure.StorageUndecided, failure)
    }
}
