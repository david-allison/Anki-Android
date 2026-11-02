// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Pankaj Gupta <pankajgupta0695@gmail.com>

package com.ichi2.preferences

import android.widget.EditText
import androidx.preference.PreferenceDialogFragmentCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.R
import com.ichi2.testutils.AndroidTest
import com.ichi2.testutils.EmptyApplication
import com.ichi2.testutils.targetContext
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class NumberRangePreferenceCompatTest : AndroidTest {
    private val preferenceMock: NumberRangePreferenceCompat = mockk(relaxed = true)

    @Test
    @Suppress("DEPRECATION") // Matches the ARSC-backed attribute setup in SliderPreferenceTest.
    fun `XML bounds are passed to the JVM input policy`() {
        val attrs =
            Robolectric
                .buildAttributeSet()
                .addAttribute(R.attr.min, "10")
                .addAttribute(R.attr.max, "20")
                .build()
        val preference = NumberRangePreferenceCompat(targetContext, attrs)

        assertThat(preference.getValidatedRangeFromInt(5), equalTo(10))
        assertThat(preference.getValidatedRangeFromInt(25), equalTo(20))
        assertThat(preference.getValidatedRangeFromString("15"), equalTo(15))
        assertThat(preference.getValidatedRangeFromString(""), equalTo(10))
        assertThat(preference.getValidatedRangeFromString("abc"), equalTo(10))
    }

    @Test
    fun onDialogClosedWithBlankTextDoesNotCallSetValue() {
        clearMocks(preferenceMock)
        every { preferenceMock.getValidatedRangeFromString(any()) } answers { 0 }
        every { preferenceMock.callChangeListener(any()) } returns true

        val dialogFragment = createDialogWithText("", preferenceMock)
        dialogFragment.onDialogClosed(true)

        verify(exactly = 0) { preferenceMock.setValue(any<Int>()) }
    }

    @Test
    fun onDialogClosedWithCancelledDialogDoesNotCallSetValue() {
        clearMocks(preferenceMock)
        every { preferenceMock.getValidatedRangeFromString(any()) } answers { 99 }
        every { preferenceMock.callChangeListener(any()) } returns true

        val dialogFragment = createDialogWithText("99", preferenceMock)
        dialogFragment.onDialogClosed(false)

        verify(exactly = 0) { preferenceMock.setValue(any<Int>()) }
    }

    @Test
    fun onDialogClosedWithValidTextCallsSetValue() {
        clearMocks(preferenceMock)
        every { preferenceMock.getValidatedRangeFromString("75") } returns 75
        every { preferenceMock.callChangeListener(75) } returns true

        val dialogFragment = createDialogWithText("75", preferenceMock)
        dialogFragment.onDialogClosed(true)

        verify(exactly = 1) { preferenceMock.setValue(75) }
    }

    private fun createDialogWithText(
        text: String,
        pref: NumberRangePreferenceCompat,
    ): NumberRangePreferenceCompat.NumberRangeDialogFragmentCompat {
        val dialogFragment = NumberRangePreferenceCompat.NumberRangeDialogFragmentCompat()

        val preferenceField = PreferenceDialogFragmentCompat::class.java.getDeclaredField("mPreference")
        preferenceField.isAccessible = true
        preferenceField.set(dialogFragment, pref)

        val editText = EditText(targetContext)
        editText.setText(text)
        dialogFragment.editText = editText

        return dialogFragment
    }
}
