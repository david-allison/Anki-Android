// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.servicemodel

import androidx.core.content.edit
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.ichi2.anki.servicelayer.PreferenceUpgradeService
import com.ichi2.anki.servicelayer.PreferenceUpgradeService.PREF_LAST_UPGRADE_VERSION
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreviousUpgradeVersionTest {
    private val preferences = SPMockBuilder().createSharedPreferences()

    @Test
    fun `versions before 2_0_2 force the database check`() {
        preferences.edit { putString(PREF_LAST_UPGRADE_VERSION, "2.0.1") }
        assertEquals(0L, PreferenceUpgradeService.getPreviousVersion(preferences, 20900302L))
        assertFalse(preferences.contains(PREF_LAST_UPGRADE_VERSION))
    }

    @Test
    fun `2_0_2 maps to its legacy upgrade identifier`() {
        preferences.edit { putString(PREF_LAST_UPGRADE_VERSION, "2.0.2") }
        assertEquals(40L, PreferenceUpgradeService.getPreviousVersion(preferences, 20900302L))
        assertFalse(preferences.contains(PREF_LAST_UPGRADE_VERSION))
    }

    @Test
    fun `integer version codes are read and cleared for replacement with a long`() {
        preferences.edit { putInt(PREF_LAST_UPGRADE_VERSION, 20800301) }
        assertEquals(20800301L, PreferenceUpgradeService.getPreviousVersion(preferences, 20900301L))
        assertFalse(preferences.contains(PREF_LAST_UPGRADE_VERSION))
    }

    @Test
    fun `long version codes remain stored`() {
        preferences.edit { putLong(PREF_LAST_UPGRADE_VERSION, 20900301L) }
        assertEquals(20900301L, PreferenceUpgradeService.getPreviousVersion(preferences, 20900302L))
        assertTrue(preferences.contains(PREF_LAST_UPGRADE_VERSION))
    }

    @Test
    fun `missing version codes default to the current release`() {
        assertEquals(20900302L, PreferenceUpgradeService.getPreviousVersion(preferences, 20900302L))
        assertFalse(preferences.contains(PREF_LAST_UPGRADE_VERSION))
    }
}
