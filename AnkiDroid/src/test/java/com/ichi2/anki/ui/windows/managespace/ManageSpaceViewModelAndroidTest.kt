// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.managespace

import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class ManageSpaceViewModelAndroidTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Test
    fun `the storage screen creates and restores its model with the Android storage adapter`() {
        val controller = startActivityControllerNormallyOpenCollectionWithIntent(ManageSpaceActivity::class.java, null)

        fun model(): ManageSpaceViewModel {
            val fragment =
                controller
                    .get()
                    .supportFragmentManager.fragments
                    .filterIsInstance<ManageSpaceFragment>()
                    .single()
            assertNotNull(fragment.view)
            return ViewModelProvider(fragment)[ManageSpaceViewModel::class.java]
        }
        advanceRobolectricLooperUntil { model().flowOfDeleteEverythingSize.value !is Size.Calculating }
        assertIs<Size.Bytes>(model().flowOfDeleteEverythingSize.value)
        controller.recreate()
        advanceRobolectricLooperUntil { model().flowOfDeleteEverythingSize.value !is Size.Calculating }
        assertIs<Size.Bytes>(model().flowOfDeleteEverythingSize.value)
    }
}
