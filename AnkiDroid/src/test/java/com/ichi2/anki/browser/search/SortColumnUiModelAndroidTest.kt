// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser.search

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.browser.search.SortOrderBottomSheetFragment.ColumnUiModel
import com.ichi2.testutils.EmptyApplication
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class SortColumnUiModelAndroidTest {
    @Test
    fun `no ordering has a translated label`() {
        assertEquals("No sorting", ColumnUiModel.NoOrdering.getLabel(ApplicationProvider.getApplicationContext()))
    }
}
