// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 Eric Li <ericli3690@gmail.com>

package com.ichi2.anki.reviewreminders

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.IdRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.navigationBars
import androidx.core.view.WindowInsetsCompat.Type.statusBars
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.test.core.app.ActivityScenario
import com.google.android.material.appbar.AppBarLayout
import com.ichi2.anki.R
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.StudyOptionsActivity
import com.ichi2.anki.common.destinations.StudyOptionsDestination
import com.ichi2.anki.common.destinations.launchActivity
import com.ichi2.anki.preferences.PreferencesActivity
import com.ichi2.anki.preferences.PreferencesFragment
import com.ichi2.anki.reviewreminders.ScheduleRemindersFragment.FragmentHost
import com.ichi2.anki.utils.ConfigAwareSingleFragmentActivity
import com.ichi2.anki.withDeckPicker
import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.testutils.insetsOf
import com.ichi2.utils.dp
import org.junit.Test

/**
 * Covers all [FragmentHost] configurations of the fragment.
 */
class ReviewRemindersScreenshotTest : ScreenshotTest() {
    @Test
    fun `settings host`() {
        captureSettingsHost("settingsHost")
    }

    @Test
    fun `settings host tablet`() {
        setTabletQualifiers()
        // The toolbar is not collapsible on wide screens
        captureSettingsHost("settingsHostTablet", captureScrolled = false)
    }

    private fun captureSettingsHost(
        prefix: String,
        captureScrolled: Boolean = true,
    ) {
        ActivityScenario.launch<PreferencesActivity>(PreferencesActivity.getIntent(targetContext)).use { scenario ->
            scenario.onActivity { activity ->
                val fm = (activity.fragment as PreferencesFragment).childFragmentManager
                commitScheduleRemindersAndCapture(
                    fragmentManager = fm,
                    containerId = R.id.settings_container,
                    host = FragmentHost.SETTINGS,
                    scope = ReviewReminderScope.Global,
                    prefix = prefix,
                )
                if (captureScrolled) {
                    fm
                        .findFragmentById(R.id.settings_container)
                        ?.view
                        ?.findViewById<AppBarLayout>(R.id.appbar)
                        ?.setExpanded(false, false)
                    advanceRobolectricLooper()
                    captureScreen("${prefix}_scheduleReminders_scrolled")
                }
                commitTroubleshootingAndCapture(
                    fragmentManager = fm,
                    containerId = R.id.settings_container,
                    host = FragmentHost.SETTINGS,
                    prefix = prefix,
                )
            }
        }
    }

    @Test
    fun `study options fragment host`() {
        setTabletQualifiers()
        withDeckPicker(deckCount = 1, withCards = true) { deckPicker ->
            val deckId = addDeck("Test Deck")
            commitScheduleRemindersAndCapture(
                fragmentManager = deckPicker.supportFragmentManager,
                containerId = R.id.studyoptions_fragment,
                host = FragmentHost.STUDY_OPTIONS_FRAGMENT,
                scope = ReviewReminderScope.DeckSpecific(deckId),
                prefix = "studyOptionsFragmentHost",
            )
            commitTroubleshootingAndCapture(
                fragmentManager = deckPicker.supportFragmentManager,
                containerId = R.id.studyoptions_fragment,
                host = FragmentHost.STUDY_OPTIONS_FRAGMENT,
                prefix = "studyOptionsFragmentHost",
            )
        }
        BackupManagerTestUtilities.reset()
    }

    @Test
    fun `study options frame host`() {
        val deckId = addDeck("Test Deck")
        launchActivity<StudyOptionsActivity>(StudyOptionsDestination).use { scenario ->
            scenario.onActivity { activity ->
                commitScheduleRemindersAndCapture(
                    fragmentManager = activity.supportFragmentManager,
                    containerId = R.id.studyoptions_frame,
                    host = FragmentHost.STUDY_OPTIONS_FRAME,
                    scope = ReviewReminderScope.DeckSpecific(deckId),
                    prefix = "studyOptionsFrameHost",
                )
                commitTroubleshootingAndCapture(
                    fragmentManager = activity.supportFragmentManager,
                    containerId = R.id.studyoptions_frame,
                    host = FragmentHost.STUDY_OPTIONS_FRAME,
                    prefix = "studyOptionsFrameHost",
                )
            }
        }
    }

    @Test
    fun `standalone activity host`() {
        val intent = ScheduleRemindersFragment.getIntent(targetContext, ReviewReminderScope.Global)
        ActivityScenario.launch<ConfigAwareSingleFragmentActivity>(intent).use { scenario ->
            advanceRobolectricLooper()
            scenario.onActivity { activity ->
                captureScreen("standaloneActivityHost_scheduleReminders")
                commitTroubleshootingAndCapture(
                    fragmentManager = activity.supportFragmentManager,
                    containerId = R.id.fragment_container,
                    host = FragmentHost.STANDALONE_ACTIVITY,
                    prefix = "standaloneActivityHost",
                )
            }
        }
    }

    @Test
    fun `standalone activity host with system bars`() {
        val intent = ScheduleRemindersFragment.getIntent(targetContext, ReviewReminderScope.Global)
        ActivityScenario.launch<ConfigAwareSingleFragmentActivity>(intent).use { scenario ->
            advanceRobolectricLooper()
            scenario.onActivity { activity ->
                activity.simulateSystemBars()
                captureScreen("standaloneActivityHost_systemBars")
            }
        }
    }

    /**
     * Robolectric reports zero system-bar insets by default. Inject realistic ones so the
     * fragment's edge-to-edge layout responds as it would on a real device, and overlay
     * translucent bands where the bars would sit to show whether content clears them.
     *
     * The insets are dispatched at `android.R.id.content`: this window has not opted into
     * edge-to-edge, so insets dispatched at the decor are consumed before reaching the views.
     */
    private fun Activity.simulateSystemBars() {
        val statusBarHeight = 24.dp
        val navBarHeight = 48.dp
        val insets =
            with(targetContext) {
                WindowInsetsCompat
                    .Builder()
                    .setInsets(statusBars(), insetsOf(top = statusBarHeight))
                    .setInsets(navigationBars(), insetsOf(bottom = navBarHeight))
                    .build()
            }
        ViewCompat.dispatchApplyWindowInsets(findViewById(android.R.id.content), insets)

        val decor = window.decorView as ViewGroup
        listOf(
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, statusBarHeight.toPx(targetContext), Gravity.TOP),
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, navBarHeight.toPx(targetContext), Gravity.BOTTOM),
        ).forEach { params ->
            decor.addView(View(this).apply { setBackgroundColor(0x80000000.toInt()) }, params)
        }
        advanceRobolectricLooper()
    }

    private fun commitScheduleRemindersAndCapture(
        fragmentManager: FragmentManager,
        @IdRes containerId: Int,
        host: FragmentHost,
        scope: ReviewReminderScope,
        prefix: String,
    ) {
        fragmentManager.commit {
            replace(containerId, ScheduleRemindersFragment.newInstance(scope, host))
        }
        advanceRobolectricLooper()
        captureScreen("${prefix}_scheduleReminders")
    }

    private fun commitTroubleshootingAndCapture(
        fragmentManager: FragmentManager,
        @IdRes containerId: Int,
        host: FragmentHost,
        prefix: String,
    ) {
        fragmentManager.commit {
            replace(containerId, ReminderTroubleshootingFragment.newInstance(host))
            addToBackStack(null)
        }
        advanceRobolectricLooper()
        captureScreen("${prefix}_troubleshooting")
    }
}
