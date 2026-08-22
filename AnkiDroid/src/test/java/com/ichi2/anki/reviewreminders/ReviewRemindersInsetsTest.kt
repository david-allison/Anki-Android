// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewreminders

import android.app.Activity
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.navigationBars
import androidx.core.view.WindowInsetsCompat.Type.statusBars
import androidx.core.view.isVisible
import androidx.core.view.marginBottom
import androidx.core.view.marginRight
import androidx.fragment.app.commit
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.StudyOptionsActivity
import com.ichi2.anki.common.destinations.StudyOptionsDestination
import com.ichi2.anki.common.destinations.launchActivity
import com.ichi2.anki.databinding.FragmentScheduleRemindersBinding
import com.ichi2.anki.preferences.PreferencesActivity
import com.ichi2.anki.preferences.PreferencesFragment
import com.ichi2.anki.reviewreminders.ScheduleRemindersFragment.FragmentHost
import com.ichi2.anki.utils.ConfigAwareSingleFragmentActivity
import com.ichi2.anki.withDeckPicker
import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.testutils.insetsOf
import com.ichi2.utils.Dp
import com.ichi2.utils.dp
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment

/**
 * Edge-to-edge inset handling for [ScheduleRemindersFragment] and [ReminderTroubleshootingFragment]
 * across their [FragmentHost]s.
 *
 * The hosts fall into two groups:
 *
 * - hosts where the fragment fills the window ([FragmentHost.SETTINGS] and
 *   [FragmentHost.STANDALONE_ACTIVITY]): the fragment applies the system bar insets itself;
 * - hosts which apply the insets to the fragment's container ([FragmentHost.STUDY_OPTIONS_FRAME]
 *   and [FragmentHost.STUDY_OPTIONS_FRAGMENT]): the fragment must not apply them again.
 */
@RunWith(AndroidJUnit4::class)
class ReviewRemindersInsetsTest : RobolectricTest() {
    /** The height of the simulated status bar */
    private val statusBarHeight = 24.dp

    /** The size of the simulated navigation bar: its height, or its width when on the side of the screen */
    private val navigationBarSize = 48.dp

    /** The 'add reminder' button's margin, from the layout XML */
    private val fabMargin = 16.dp

    /** The list's bottom padding, from the layout XML: lets content scroll clear of the 'add reminder' button */
    private val listBottomPadding = 84.dp

    @Test
    fun `standalone host - toolbar content clears the status bar and cutout`() =
        withStandaloneScheduleReminders { activity, binding ->
            activity.dispatchInsets(cutoutLeft = 32.dp)

            assertThat(
                "toolbar content is pushed clear of the status bar",
                binding.appbar.paddingTop,
                equalTo(statusBarHeight.toPx(targetContext)),
            )
            assertThat(
                "toolbar content clears the cutout",
                binding.appbar.paddingLeft,
                equalTo(32.dp.toPx(targetContext)),
            )
        }

    @Test
    fun `standalone host - button and list content clear the navigation bar`() =
        withStandaloneScheduleReminders { activity, binding ->
            activity.dispatchInsets(navBarBottom = navigationBarSize)

            assertThat(
                "the 'add reminder' button rests above the navigation bar",
                binding.floatingActionButtonAdd.marginBottom,
                equalTo((fabMargin + navigationBarSize).toPx(targetContext)),
            )
            assertThat(
                "scrolled list content clears the navigation bar",
                binding.recyclerView.paddingBottom,
                equalTo((listBottomPadding + navigationBarSize).toPx(targetContext)),
            )
        }

    @Test
    fun `standalone host - content clears a side navigation bar`() =
        withStandaloneScheduleReminders { activity, binding ->
            // landscape with 3-button navigation: the navigation bar is a side inset
            activity.dispatchInsets(navBarRight = navigationBarSize)

            assertThat(
                "toolbar content clears the side navigation bar",
                binding.appbar.paddingRight,
                equalTo(navigationBarSize.toPx(targetContext)),
            )
            assertThat(
                "list content clears the side navigation bar",
                binding.recyclerView.paddingRight,
                equalTo(navigationBarSize.toPx(targetContext)),
            )
            assertThat(
                "the 'add reminder' button clears the side navigation bar",
                binding.floatingActionButtonAdd.marginRight,
                equalTo((fabMargin + navigationBarSize).toPx(targetContext)),
            )
        }

    @Test
    fun `standalone host - troubleshooting content clears the system bars`() =
        withStandaloneScheduleReminders { activity, _ ->
            activity.supportFragmentManager.commit {
                replace(
                    R.id.fragment_container,
                    ReminderTroubleshootingFragment.newInstance(FragmentHost.STANDALONE_ACTIVITY),
                )
            }
            advanceRobolectricLooper()
            activity.dispatchInsets(navBarBottom = navigationBarSize)

            val fragmentView =
                activity.supportFragmentManager
                    .findFragmentById(R.id.fragment_container)!!
                    .requireView()
            assertThat(
                "content is pushed clear of the status bar",
                fragmentView.paddingTop,
                equalTo(statusBarHeight.toPx(targetContext)),
            )
            assertThat(
                "content clears the navigation bar",
                fragmentView.paddingBottom,
                equalTo(navigationBarSize.toPx(targetContext)),
            )
        }

    @Test
    fun `settings host - collapsible toolbar clears the status bar`() {
        ActivityScenario.launch<PreferencesActivity>(PreferencesActivity.getIntent(targetContext)).use { scenario ->
            scenario.onActivity { activity ->
                val fm = (activity.fragment as PreferencesFragment).childFragmentManager
                fm.commit {
                    replace(
                        R.id.settings_container,
                        ScheduleRemindersFragment.newInstance(ReviewReminderScope.Global, FragmentHost.SETTINGS),
                    )
                }
                advanceRobolectricLooper()
                activity.dispatchInsets(navBarBottom = navigationBarSize)

                val binding =
                    FragmentScheduleRemindersBinding.bind(
                        fm.findFragmentById(R.id.settings_container)!!.requireView(),
                    )
                assertThat(
                    "the root does not pad itself; the app bar handles the inset",
                    binding.root.paddingTop,
                    equalTo(0),
                )
                assertThat(
                    "the collapsible toolbar is pushed clear of the status bar",
                    binding.toolbar.top,
                    equalTo(statusBarHeight.toPx(targetContext)),
                )
                assertThat(
                    "the 'add reminder' button rests above the navigation bar",
                    binding.floatingActionButtonAdd.marginBottom,
                    equalTo((fabMargin + navigationBarSize).toPx(targetContext)),
                )
            }
        }
    }

    @Test
    fun `study options frame host - insets are applied by the host, not the fragment`() {
        val deckId = addDeck("Test Deck")
        launchActivity<StudyOptionsActivity>(StudyOptionsDestination).use { scenario ->
            scenario.onActivity { activity ->
                activity.supportFragmentManager.commit {
                    replace(
                        R.id.studyoptions_frame,
                        ScheduleRemindersFragment.newInstance(
                            ReviewReminderScope.DeckSpecific(deckId),
                            FragmentHost.STUDY_OPTIONS_FRAME,
                        ),
                    )
                }
                advanceRobolectricLooper()
                activity.dispatchInsets(navBarBottom = navigationBarSize)

                val binding =
                    FragmentScheduleRemindersBinding.bind(
                        activity.supportFragmentManager
                            .findFragmentById(R.id.studyoptions_frame)!!
                            .requireView(),
                    )
                assertThat(
                    "the host clears the navigation bar",
                    (binding.root.parent as View).paddingBottom,
                    equalTo(navigationBarSize.toPx(targetContext)),
                )
                assertThat(
                    "the fragment does not apply the top inset again",
                    binding.root.paddingTop,
                    equalTo(0),
                )
                assertThat(
                    "the fragment does not apply the bottom inset again",
                    binding.root.paddingBottom,
                    equalTo(0),
                )
                assertThat(
                    "the toolbar is provided by the host",
                    binding.appbar.isVisible,
                    equalTo(false),
                )
                assertThat(
                    "the fragment does not move the 'add reminder' button again",
                    binding.floatingActionButtonAdd.marginBottom,
                    equalTo(fabMargin.toPx(targetContext)),
                )
            }
        }
    }

    @Test
    fun `study options frame host - troubleshooting does not apply insets of its own`() {
        launchActivity<StudyOptionsActivity>(StudyOptionsDestination).use { scenario ->
            scenario.onActivity { activity ->
                activity.supportFragmentManager.commit {
                    replace(
                        R.id.studyoptions_frame,
                        ReminderTroubleshootingFragment.newInstance(FragmentHost.STUDY_OPTIONS_FRAME),
                    )
                }
                advanceRobolectricLooper()
                activity.dispatchInsets(navBarBottom = navigationBarSize)

                val fragmentView =
                    activity.supportFragmentManager
                        .findFragmentById(R.id.studyoptions_frame)!!
                        .requireView()
                assertThat(
                    "the fragment does not apply the top inset again",
                    fragmentView.paddingTop,
                    equalTo(0),
                )
                assertThat(
                    "the fragment does not apply the bottom inset again",
                    fragmentView.paddingBottom,
                    equalTo(0),
                )
            }
        }
    }

    @Test
    fun `study options fragment host - side panel toolbar is not offset by the status bar`() {
        RuntimeEnvironment.setQualifiers(RobolectricDeviceQualifiers.MediumTablet)
        withDeckPicker(deckCount = 1, withCards = true) { deckPicker ->
            val deckId = addDeck("Panel Deck")
            deckPicker.supportFragmentManager.commit {
                replace(
                    R.id.studyoptions_fragment,
                    ScheduleRemindersFragment.newInstance(
                        ReviewReminderScope.DeckSpecific(deckId),
                        FragmentHost.STUDY_OPTIONS_FRAGMENT,
                    ),
                )
            }
            advanceRobolectricLooper()
            deckPicker.dispatchInsets(navBarBottom = navigationBarSize)

            val binding =
                FragmentScheduleRemindersBinding.bind(
                    deckPicker.supportFragmentManager
                        .findFragmentById(R.id.studyoptions_fragment)!!
                        .requireView(),
                )
            assertThat(
                "the host clears the navigation bar",
                (binding.root.parent as View).paddingBottom,
                equalTo(navigationBarSize.toPx(targetContext)),
            )
            // The side panel sits below the DeckPicker toolbar, which already clears the status
            // bar: the panel's own toolbar must not absorb the status bar inset again
            assertThat(
                "the panel toolbar is not padded by the status bar",
                binding.appbar.paddingTop,
                equalTo(0),
            )
            assertThat(
                "the panel toolbar is not pushed down by the status bar",
                binding.nonCollapsibleToolbar.top,
                equalTo(0),
            )
            assertThat(
                "the fragment does not move the 'add reminder' button again",
                binding.floatingActionButtonAdd.marginBottom,
                equalTo(fabMargin.toPx(targetContext)),
            )
        }
        BackupManagerTestUtilities.reset()
    }

    /**
     * Dispatches realistic system-bar insets, which Robolectric otherwise reports as zero.
     *
     * Dispatches at `android.R.id.content` rather than at the decor: for windows which have not
     * (yet) opted into edge-to-edge, Robolectric's decor and AppCompat's sub-decor consume the
     * insets before they reach the activity's views. Edge-to-edge devices deliver the insets to
     * the views, which is the behavior under test.
     */
    private fun Activity.dispatchInsets(
        navBarBottom: Dp = 0.dp,
        navBarRight: Dp = 0.dp,
        cutoutLeft: Dp = 0.dp,
    ) {
        val insets =
            with(targetContext) {
                WindowInsetsCompat
                    .Builder()
                    .setInsets(statusBars(), insetsOf(top = statusBarHeight))
                    .setInsets(navigationBars(), insetsOf(right = navBarRight, bottom = navBarBottom))
                    .setInsets(displayCutout(), insetsOf(left = cutoutLeft))
                    .build()
            }
        ViewCompat.dispatchApplyWindowInsets(findViewById(android.R.id.content), insets)
        // relayout synchronously so the insets affect view positions before the test asserts
        val decor = window.decorView
        decor.measure(
            View.MeasureSpec.makeMeasureSpec(decor.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(decor.height, View.MeasureSpec.EXACTLY),
        )
        decor.layout(0, 0, decor.width, decor.height)
    }

    private fun withStandaloneScheduleReminders(block: (ConfigAwareSingleFragmentActivity, FragmentScheduleRemindersBinding) -> Unit) {
        val intent = ScheduleRemindersFragment.getIntent(targetContext, ReviewReminderScope.Global)
        ActivityScenario.launch<ConfigAwareSingleFragmentActivity>(intent).use { scenario ->
            advanceRobolectricLooper()
            scenario.onActivity { activity ->
                block(activity, FragmentScheduleRemindersBinding.bind(activity.fragment!!.requireView()))
            }
        }
    }
}
