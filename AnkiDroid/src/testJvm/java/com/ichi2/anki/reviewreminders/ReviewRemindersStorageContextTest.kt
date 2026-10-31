// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewreminders

import android.app.Application
import androidx.core.content.edit
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.ichi2.anki.common.android.ApplicationContextInitializer
import com.ichi2.testutils.JvmApplicationRule
import io.mockk.every
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class ReviewRemindersStorageContextTest {
    @get:Rule
    val applicationContext = JvmApplicationRule()

    @Test
    fun `reminder storage follows the active application context`() {
        val original = ReviewRemindersDatabase.remindersSharedPrefs
        original.edit { putString("sentinel", "original context") }
        val replacementPreferences = SPMockBuilder().createSharedPreferences()
        val replacement = mockk<Application>()
        every { replacement.getSharedPreferences(any(), any()) } returns replacementPreferences

        try {
            ApplicationContextInitializer.setInstance(replacement)
            assertSame(replacementPreferences, ReviewRemindersDatabase.remindersSharedPrefs)
            assertNull(ReviewRemindersDatabase.remindersSharedPrefs.getString("sentinel", null))
        } finally {
            ApplicationContextInitializer.setInstance(applicationContext.application)
        }

        assertSame(original, ReviewRemindersDatabase.remindersSharedPrefs)
    }
}
