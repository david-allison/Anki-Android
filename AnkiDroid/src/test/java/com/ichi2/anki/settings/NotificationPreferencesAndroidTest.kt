// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.settings

import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.testutils.AndroidTest
import com.ichi2.testutils.EmptyApplication
import com.ichi2.testutils.targetContext
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.ichi2.anki.common.android.R as CommonR

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class NotificationPreferencesAndroidTest : AndroidTest {
    private val preferences = targetContext.sharedPrefs()
    private val notifications = PrefsRepository(targetContext)

    @Test
    fun `existing reminder preference is read and updated without changing its key`() {
        preferences.edit { putBoolean("newReviewReminders", true) }
        assertTrue(notifications.newReviewRemindersEnabled)
        notifications.newReviewRemindersEnabled = false
        assertFalse(preferences.getBoolean("newReviewReminders", true))
        assertEquals("minimumCardsDueForNotification", notifications.key(CommonR.string.pref_notifications_minimum_cards_due_key))
    }
}
