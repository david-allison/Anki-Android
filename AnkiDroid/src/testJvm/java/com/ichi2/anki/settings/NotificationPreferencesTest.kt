// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.settings

import androidx.core.content.edit
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.testutils.jvmPreferences
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.ichi2.anki.common.android.R as CommonR

class NotificationPreferencesTest {
    private val notifications = jvmPreferences()
    private val preferences = notifications.sharedPrefs

    @Test
    fun `both notification systems default to disabled`() {
        preferences.edit { clear() }
        assertFalse(notifications.newReviewRemindersEnabled)
        assertFalse(notifications.legacyNotificationsEnabled)
    }

    @Test
    fun `legacy notification threshold retains its disabled boundary`() {
        val key = notifications.key(CommonR.string.pref_notifications_minimum_cards_due_key)
        preferences.edit { putString(key, "999999") }
        assertTrue(notifications.legacyNotificationsEnabled)
        preferences.edit { putString(key, "1000000") }
        assertFalse(notifications.legacyNotificationsEnabled)
    }
}
