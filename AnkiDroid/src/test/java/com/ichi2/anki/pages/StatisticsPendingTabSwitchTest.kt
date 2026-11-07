// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.search.SearchNode
import com.ichi2.anki.BottomNavController.NavigationItem
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.setIntroductionSlidesShown
import com.ichi2.testutils.withBooleanPreference
import com.ichi2.testutils.withCollectionAccessOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Issue 23061: Statistics must not open the browser after Home has been selected.
 *
 * Requires the awaitPageShown UI handler API from https://github.com/ankidroid/Anki-Android/pull/23031.
 */
@RunWith(AndroidJUnit4::class)
class StatisticsPendingTabSwitchTest : RobolectricTest() {
    @Test
    fun `search finishing after Home selection waits until Statistics is selected again`() =
        withSearchVisibilityCheck {
            setIntroductionSlidesShown(true)
            val activity = startRegularActivity<DeckPicker>()
            val bottomNav = activity.binding.bottomNavigation!!
            bottomNav.selectedItemId = NavigationItem.STATS.id
            advanceRobolectricLooper()
            val page = activity.supportFragmentManager.findFragmentByTag(NavigationItem.STATS.tag) as Statistics
            val shadow = shadowOf(activity)
            assertNull(shadow.nextStartedActivity)
            val collectionDispatcher = StandardTestDispatcher()

            runBlocking {
                withCollectionAccessOn(collectionDispatcher) {
                    val request =
                        activity.lifecycleScope.async {
                            page.handlePostRequest(
                                PostRequestUri("/_anki/searchInBrowser"),
                                SearchNode.getDefaultInstance().toByteArray(),
                            )
                        }
                    assertFalse(request.isCompleted)

                    // Home's input precedes the collection reply, but its old hide transaction was queued behind it.
                    Handler(Looper.getMainLooper()).post {
                        bottomNav.selectedItemId = NavigationItem.HOME.id
                        assertFalse(activity.binding.bottomNavFragmentContainer!!.isVisible)
                    }
                    runBlocking(Dispatchers.Default) { collectionDispatcher.scheduler.runCurrent() }
                    advanceRobolectricLooper()
                    assertEquals(NavigationItem.HOME.id, bottomNav.selectedItemId)
                    assertTrue(page.isHidden)
                    val launchOverHome = shadow.nextStartedActivity

                    bottomNav.selectedItemId = NavigationItem.STATS.id
                    advanceRobolectricLooper()
                    request.await()

                    assertNull(launchOverHome, "A search must not open the browser after Home is selected")
                    assertEquals(CardBrowser::class.java.name, assertNotNull(shadow.nextStartedActivity).component?.className)
                }
            }
        }

    @Test
    fun `rapid Statistics then Home selections do not release a waiting search over Home`() =
        withSearchVisibilityCheck {
            setIntroductionSlidesShown(true)
            val activity = startRegularActivity<DeckPicker>()
            val bottomNav = activity.binding.bottomNavigation!!
            bottomNav.selectedItemId = NavigationItem.STATS.id
            advanceRobolectricLooper()
            val page = activity.supportFragmentManager.findFragmentByTag(NavigationItem.STATS.tag) as Statistics
            bottomNav.selectedItemId = NavigationItem.HOME.id
            advanceRobolectricLooper()
            val shadow = shadowOf(activity)
            assertNull(shadow.nextStartedActivity)
            val collectionDispatcher = StandardTestDispatcher()

            runBlocking {
                withCollectionAccessOn(collectionDispatcher) {
                    val request =
                        activity.lifecycleScope.async {
                            page.handlePostRequest(
                                PostRequestUri("/_anki/searchInBrowser"),
                                SearchNode.getDefaultInstance().toByteArray(),
                            )
                        }
                    runBlocking(Dispatchers.Default) { collectionDispatcher.scheduler.runCurrent() }
                    advanceRobolectricLooper()
                    assertFalse(request.isCompleted, "The search must wait while Statistics is hidden")

                    // A synchronous show can legitimately open the browser before the Home input arrives.
                    var launchBeforeHome: Intent? = null
                    val handler = Handler(Looper.getMainLooper())
                    handler.post { bottomNav.selectedItemId = NavigationItem.STATS.id }
                    handler.post {
                        launchBeforeHome = shadow.nextStartedActivity
                        bottomNav.selectedItemId = NavigationItem.HOME.id
                    }
                    advanceRobolectricLooper()
                    val launchOverHome = shadow.nextStartedActivity

                    bottomNav.selectedItemId = NavigationItem.STATS.id
                    advanceRobolectricLooper()
                    request.await()

                    assertNull(launchOverHome, "An intermediate show must not release a search after Home is selected")
                    assertEquals(
                        CardBrowser::class.java.name,
                        assertNotNull(launchBeforeHome ?: shadow.nextStartedActivity).component?.className,
                    )
                }
            }
        }

    /** Verify that the real search handler waits for its originating page before opening the browser. */
    private fun withSearchVisibilityCheck(test: () -> Unit) =
        withBooleanPreference(R.string.dev_bottom_nav_key, true) {
            val originalHandler = uiMethods.getValue("searchInBrowser")
            var visibilityChecks = 0
            uiMethods["searchInBrowser"] = { bytes, awaitPageShown ->
                originalHandler.invoke(this, bytes) {
                    visibilityChecks++
                    awaitPageShown()
                }
            }
            try {
                test()
                assertEquals(1, visibilityChecks, "The search must check page visibility before opening the browser")
            } finally {
                uiMethods["searchInBrowser"] = originalHandler
            }
        }
}
