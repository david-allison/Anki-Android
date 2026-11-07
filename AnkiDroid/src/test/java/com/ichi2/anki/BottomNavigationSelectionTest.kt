// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.core.view.isVisible
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.BottomNavController.NavigationItem
import com.ichi2.testutils.withBooleanPreference
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class BottomNavigationSelectionTest : RobolectricTest() {
    @Test
    fun `Home hides Statistics before tab selection returns`() =
        withBottomNavigation {
            binding.bottomNavigation!!.selectedItemId = NavigationItem.STATS.id
            advanceRobolectricLooper()
            val statistics = assertNotNull(supportFragmentManager.findFragmentByTag(NavigationItem.STATS.tag))

            binding.bottomNavigation!!.selectedItemId = NavigationItem.HOME.id

            assertFalse(binding.bottomNavFragmentContainer!!.isVisible)
            assertTrue(statistics.isHidden, "A pending statistics request must see Home's selection immediately")
        }

    @Test
    fun `returning to Statistics shows it before tab selection returns`() =
        withBottomNavigation {
            binding.bottomNavigation!!.selectedItemId = NavigationItem.STATS.id
            advanceRobolectricLooper()
            val statistics = assertNotNull(supportFragmentManager.findFragmentByTag(NavigationItem.STATS.tag))
            binding.bottomNavigation!!.selectedItemId = NavigationItem.HOME.id
            advanceRobolectricLooper()
            assertTrue(statistics.isHidden)

            binding.bottomNavigation!!.selectedItemId = NavigationItem.STATS.id

            assertTrue(binding.bottomNavFragmentContainer!!.isVisible)
            assertFalse(statistics.isHidden, "Showing Statistics must not be deferred until after another tab is selected")
        }

    @Test
    fun `a quick first visit to Statistics then Home leaves Statistics hidden`() =
        withBottomNavigation {
            binding.bottomNavigation!!.selectedItemId = NavigationItem.STATS.id
            binding.bottomNavigation!!.selectedItemId = NavigationItem.HOME.id
            advanceRobolectricLooper()

            val statistics = assertNotNull(supportFragmentManager.findFragmentByTag(NavigationItem.STATS.tag))
            assertFalse(binding.bottomNavFragmentContainer!!.isVisible)
            assertTrue(binding.deckPickerContentWrapper!!.isVisible)
            assertTrue(statistics.isHidden, "The queued Statistics transaction must not show it after Home is selected")
        }

    private fun withBottomNavigation(test: DeckPicker.() -> Unit) =
        withBooleanPreference(R.string.dev_bottom_nav_key, true) {
            setIntroductionSlidesShown(true)
            startRegularActivity<DeckPicker>().test()
        }
}
