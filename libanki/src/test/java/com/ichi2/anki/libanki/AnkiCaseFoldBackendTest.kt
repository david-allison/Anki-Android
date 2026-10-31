// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.libanki

import com.ichi2.anki.common.utils.ankiCaseFold
import com.ichi2.anki.libanki.testutils.InMemoryAnkiTest
import org.junit.Test
import kotlin.test.assertEquals

class AnkiCaseFoldBackendTest : InMemoryAnkiTest() {
    @Test
    fun `case identity agrees with native collation for all JVM case pairs`() {
        val checked = mutableSetOf<Pair<Int, Int>>()
        val arguments = mutableListOf<Any?>()

        fun checkBatch() {
            if (arguments.isEmpty()) return
            val values = List(arguments.size / 3) { "(?, ?, ?)" }.joinToString(",")
            assertEquals(
                0,
                col.db.queryScalar(
                    "WITH pairs(original, alternative, matches) AS (VALUES $values) " +
                        "SELECT count(*) FROM pairs WHERE (original = alternative COLLATE unicase) != matches",
                    *arguments.toTypedArray(),
                ),
                "Case-pair batch ending in ${arguments[arguments.size - 3]} / ${arguments[arguments.size - 2]}",
            )
            arguments.clear()
        }

        for (codePoint in 0..0x10FFFF) {
            if (codePoint in 0xD800..0xDFFF) continue
            for (alternative in intArrayOf(
                Character.toLowerCase(codePoint),
                Character.toUpperCase(codePoint),
                Character.toTitleCase(codePoint),
            )) {
                if (alternative == codePoint || !checked.add(minOf(codePoint, alternative) to maxOf(codePoint, alternative))) continue
                val original = String(Character.toChars(codePoint))
                val other = String(Character.toChars(alternative))
                arguments.add(original)
                arguments.add(other)
                arguments.add(if (original.ankiCaseFold() == other.ankiCaseFold()) 1 else 0)
                if (arguments.size == 3 * 128) checkBatch()
            }
        }
        checkBatch()
    }

    @Test
    fun `folded strings agree with native collation for every Unicode scalar`() {
        // Delimit characters so an incorrect expansion cannot consume adjacent input.
        // Small batches also identify the affected range when the backend's table changes.
        for (first in 0..0x10FFFF step 0x1000) {
            val original =
                buildString {
                    for (codePoint in first until first + 0x1000) {
                        if (codePoint in 0xD800..0xDFFF) continue
                        appendCodePoint(codePoint)
                        append('|')
                    }
                }
            assertEquals(
                1,
                col.db.queryScalar("SELECT ? = ? COLLATE unicase", original, original.ankiCaseFold()),
                "Unicode block starting at U+${first.toString(16)}",
            )
        }
    }
}
