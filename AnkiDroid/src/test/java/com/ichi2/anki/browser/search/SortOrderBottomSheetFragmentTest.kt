/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.browser.search

import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.browser.BrowserColumnKey
import com.ichi2.anki.browser.createCardBrowserViewModel
import com.ichi2.anki.browser.search.SortOrderBottomSheetFragment.Companion.ARG_CURRENT_SORT_TYPE
import com.ichi2.anki.model.SortType
import com.ichi2.anki.testutils.SingleViewModelFactory
import com.ichi2.testutils.launchFragment
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/** Tests [SortOrderBottomSheetFragment] */
@RunWith(AndroidJUnit4::class)
class SortOrderBottomSheetFragmentTest : RobolectricTest() {
    private val defaultBrowserColumnKey = BrowserColumnKey("noteFld")

    fun sampleSortType() =
        SortType.CollectionOrdering(
            key = defaultBrowserColumnKey,
            reverse = true,
        )

    @Test
    fun `sort order argument is used`() {
        withFragment(currentSortType = sampleSortType()) {
            assertEquals(
                SortType.CollectionOrdering(
                    key = defaultBrowserColumnKey,
                    reverse = true,
                ),
                this.currentSortType,
            )
        }
    }

    fun withFragment(
        currentSortType: SortType = SortType.NoOrdering,
        block: suspend SortOrderBottomSheetFragment.() -> Unit,
    ) {
        val viewModelFactory = SingleViewModelFactory.create(createCardBrowserViewModel())

        val fragmentArgs =
            bundleOf(
                ARG_CURRENT_SORT_TYPE to currentSortType,
            )
        launchFragment(fragmentArgs) {
            SortOrderBottomSheetFragment(viewModelFactory)
        }.use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)

            scenario.onFragment { fragment ->
                runBlocking {
                    block(fragment)
                }
            }
        }
    }
}
