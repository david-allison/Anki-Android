// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.testutils.EmptyApplication
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class, sdk = [24, 36])
class EditorCustomToolbarSymbolTest {
    @Test
    fun `a single visible character uses an icon button`() {
        listOf("★", "A", "字", "⭐", "🇬🇧", "e\u0301").forEach {
            assertTrue(it.isSingleToolbarSymbol(), it)
        }
    }

    @Test
    @Config(sdk = [36])
    fun `joined emoji supported by the platform remain one icon`() {
        assertTrue("👩🏽‍💻".isSingleToolbarSymbol())
    }

    @Test
    @Config(sdk = [24])
    fun `emoji sequences not joined by the platform retain text sizing`() {
        assertFalse("👩🏽‍💻".isSingleToolbarSymbol())
    }

    @Test
    fun `longer labels and blank labels retain text button sizing`() {
        listOf("", " ", "★ ", "★★", "AB", "Ruby annotation", "👩🏽‍💻⭐").forEach {
            assertFalse(it.isSingleToolbarSymbol(), it)
        }
    }
}
