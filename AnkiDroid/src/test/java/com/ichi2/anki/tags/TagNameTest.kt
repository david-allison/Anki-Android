// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import com.ichi2.testutils.JvmTest
import com.ichi2.testutils.rules.IcuCaseFoldingRule
import com.ichi2.testutils.withViewModel
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TagNameTest : JvmTest() {
    @get:Rule
    val caseFolding = IcuCaseFoldingRule()

    @Test
    fun `equivalent names persist collapse changes using the tree spelling`() =
        runTest {
            addBasicNote().update { addTag("café::child") }
            withViewModel(ManageTagsViewModel()) {
                toggleCollapsed(TagName.asValid("cafe\u0301")).join()
                assertTrue("café::child" in (state.value as ManageTagsState.Content).visibleTagNames)
                refreshTags().join()
                assertTrue("café::child" in (state.value as ManageTagsState.Content).visibleTagNames)
            }
        }

    @Test
    fun `identity agrees with the backend tag registry`() {
        val pairs =
            listOf(
                "Science" to "SCIENCE",
                "Straße" to "STRASSE",
                "σ" to "ς",
                "ﬁ" to "fi",
                "ﬃ" to "iff",
                "ﬃ" to "ffi",
                "ﬄ" to "lff",
                "ﬄ" to "ffl",
                "i" to "ı",
                "i" to "İ",
                "café" to "cafe\u0301",
                "\uD801\uDC00" to "\uD801\uDC28",
                // Backend unicase 2.6.0 predates these Unicode case pairs.
                "\ua7d0" to "\ua7d1",
                "\u1c89" to "\u1c8a",
                // Supported by the backend, but absent from older Android ICU tables.
                "\u1c90" to "\u10d0",
            )
        for ((left, right) in pairs) {
            val note =
                addBasicNote().update {
                    addTag(left)
                    addTag(right)
                }
            val backendEqual = col.getNote(note.id).tags.size == 1
            val leftTag = TagName.asValid(left)
            val rightTag = TagName.asValid(right)
            assertEquals(backendEqual, col.sameTag(leftTag, rightTag), "$left / $right")
        }
    }

    @Test
    fun `value equality preserves exact spelling without consulting the collection`() {
        val lower = TagName.asValid("science::Straße")
        val upper = TagName.asValid("SCIENCE::STRASSE")
        assertNotEquals(lower, upper)
        assertEquals(lower, TagName.asValid(lower.value))
        assertEquals(lower.hashCode(), TagName.asValid(lower.value).hashCode())
        assertNotEquals(lower.value, upper.value)
        assertEquals("science::Straße", lower.value)
        assertEquals(lower.value, lower.toString())
        assertEquals("cafe\u0301", TagName.asValid("cafe\u0301").value)
    }

    @Test
    fun `ancestry uses identity and complete components`() {
        val root = TagName.asValid("Straße::café")
        assertTrue(col.tagIsInSubtree(TagName.asValid("STRASSE::cafe\u0301"), root))
        assertTrue(col.tagIsInSubtree(TagName.asValid("STRASSE::cafe\u0301::child"), root))
        assertFalse(col.tagIsInSubtree(TagName.asValid("Straße::caféteria"), root))
        assertFalse(col.tagIsInSubtree(root, TagName.asValid("Straße::café::child")))
        assertFalse(col.tagIsInSubtree(TagName.asValid("ı::child"), TagName.asValid("i")))
    }

    @Test
    fun `normalization matches the backend rename operation`() {
        for (input in listOf("cafe\u0301", "a\tb", "a\u007fb", "::a::", "\u0085a\u00a0::\u2003b\u2003")) {
            val note = addBasicNote().update { addTag("source") }
            col.tags.rename("source", input)
            assertTrue(col.sameTag(TagName.asValid(col.getNote(note.id).tags.single()), TagName.asValid(input)), input)
        }
        // Preserve the tag manager's existing UI policy for ASCII spaces.
        assertEquals("two_words", TagName.asValid("two words").value)
        assertEquals(null, TagName.build(" \t"))
    }

    @Test
    fun `batch lookup uses backend identity and prefers existing spellings`() {
        val names = listOf("Straße", "STRASSE", "ﬃ", "iff", "ffi", "\ua7d0", "\ua7d1").map(TagName::asValid)
        val lookup = TagNameLookup(names)
        assertEquals(names.take(2), lookup.matching(col, TagName.asValid("strasse")))
        val canonical = lookup.canonicalNames(col)
        assertEquals(names[0], canonical[names[1]])
        assertEquals(names[2], canonical[names[3]])
        assertEquals(names[4], canonical[names[4]])
        assertNotEquals(canonical[names[5]], canonical[names[6]])
    }
}
