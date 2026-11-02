// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.testutils.AndroidTest
import com.ichi2.testutils.EmptyApplication
import com.ichi2.testutils.targetContext
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class BindingAndroidTest : AndroidTest {
    @Test
    @Config(qualifiers = "en")
    fun gesture_toDisplayString() {
        assertEquals("${BindingTest.GESTURE_PREFIX} Touch top", Binding.gesture(Gesture.TAP_TOP).toDisplayString())
    }

    private fun Binding.toDisplayString(): String = this.toDisplayString(targetContext)
}
