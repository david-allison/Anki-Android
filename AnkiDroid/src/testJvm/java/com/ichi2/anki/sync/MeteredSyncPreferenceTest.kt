// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.sync

import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.JvmPreferencesRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MeteredSyncPreferenceTest {
    @get:Rule
    val preferences = JvmPreferencesRule()

    @Test
    fun `setAlwaysAllow persists choice`() {
        Prefs.allowSyncOnMeteredConnections = false
        MeteredSyncPolicy.setAlwaysAllow(true)
        assertTrue(Prefs.allowSyncOnMeteredConnections)
        MeteredSyncPolicy.setAlwaysAllow(false)
        assertFalse(Prefs.allowSyncOnMeteredConnections)
    }
}
