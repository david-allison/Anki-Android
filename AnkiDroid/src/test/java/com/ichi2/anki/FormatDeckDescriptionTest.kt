// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.StudyOptionsFragment.Companion.formatDescription
import com.ichi2.testutils.EmptyApplication
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Tests for [StudyOptionsFragment.formatDescription].
 */
@RunWith(AndroidJUnit4::class) // required for String -> Spannable conversion in formatDescription
@Config(application = EmptyApplication::class)
class FormatDeckDescriptionTest {
    // Fixes for #5715: In deck description, ignore what is in style and script tag
    @Test
    fun spanTagsAreNotRemoved() {
        val result = formatDescription("""a<span style="color:red">a=1</span>a""")
        assertEquals("aa=1a", result.toString()) // Note: This is coloured red on the screen
    }

    // Begin #5188 - newlines weren't displayed
    @Test // This was originally correct
    fun brIsDisplayedAsNewline() {
        val result = formatDescription("a<br/>a")
        assertEquals("a\na", result.toString())
    }

    @Test
    fun windowsNewlinesAreNewlines() {
        val result = formatDescription("a\r\na")
        assertEquals("a\na", result.toString())
    }

    @Test
    fun unixNewlinesAreNewlines() {
        val result = formatDescription("a\na")
        assertEquals("a\na", result.toString())
    }
    // end #5188
}
