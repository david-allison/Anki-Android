// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.testutils.EmptyApplication
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class AutomaticAnswerAndroidTest {
    @Test
    fun `default instance buries cards`() {
        assertEquals(AutomaticAnswerAction.BURY_CARD, AutomaticAnswer.defaultInstance(mock()).settings.answerAction)
    }
}
