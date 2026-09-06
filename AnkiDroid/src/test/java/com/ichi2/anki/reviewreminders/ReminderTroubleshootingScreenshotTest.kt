// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewreminders

import android.app.ActivityManager
import android.app.NotificationManager
import android.os.PowerManager
import androidx.core.content.getSystemService
import androidx.fragment.app.commit
import androidx.test.core.app.ActivityScenario
import com.ichi2.anki.NotificationChannel
import com.ichi2.anki.R
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.reviewreminders.CheckResult.Warning
import com.ichi2.anki.reviewreminders.ScheduleRemindersFragment.FragmentHost
import com.ichi2.anki.utils.ConfigAwareSingleFragmentActivity
import org.junit.Before
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import android.app.NotificationChannel as AndroidNotificationChannel

class ReminderTroubleshootingScreenshotTest : ScreenshotTest() {
    @Before
    fun allowReminders() {
        val notificationManager = targetContext.getSystemService<NotificationManager>()!!
        shadowOf(notificationManager).apply {
            setNotificationsEnabled(true)
            setNotificationPolicyAccessGranted(true)
        }
        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        notificationManager.createNotificationChannel(
            AndroidNotificationChannel(
                NotificationChannel.REVIEW_REMINDERS.id,
                "Review reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        shadowOf(targetContext.getSystemService<PowerManager>()!!).apply {
            setIgnoringBatteryOptimizations(targetContext.packageName, true)
            setIsPowerSaveMode(false)
        }
        shadowOf(targetContext.getSystemService<ActivityManager>()!!).setBackgroundRestricted(false)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @Test
    fun `all checks passing`() {
        targetContext.setTroubleshootingChecks()
        captureTroubleshooting("allChecksPassing")
    }

    @Test
    fun `checks with warnings`() {
        targetContext.setTroubleshootingChecks(
            doNotDisturb = Warning,
            batteryOptimization = Warning,
            powerSavingMode = Warning,
        )

        captureTroubleshooting("checksWithWarnings")
    }

    private fun captureTroubleshooting(name: String) {
        val intent = ScheduleRemindersFragment.getIntent(targetContext, ReviewReminderScope.Global)
        ActivityScenario.launch<ConfigAwareSingleFragmentActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                activity.supportFragmentManager.commit {
                    replace(
                        R.id.fragment_container,
                        ReminderTroubleshootingFragment.newInstance(FragmentHost.STANDALONE_ACTIVITY),
                    )
                }
                advanceRobolectricLooper()
                captureScreen(name)
            }
        }
    }
}
