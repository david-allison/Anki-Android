// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.utils

import org.junit.Test
import kotlin.test.assertEquals

class AnkiCaseFoldTest {
    @Test
    fun `folding preserves Anki's Unicode version and expansion order`() {
        for ((input, expected) in listOf(
            "ABC xyz" to "abc xyz",
            "Straße" to "strasse",
            "Σςσ" to "σσσ",
            "İ" to "i\u0307",
            "ı" to "ı",
            "ﬁ" to "fi",
            "ﬃﬄ" to "ifflff",
            "ﬅﬆ" to "stst",
            "\uAB70" to "\u13A0",
            "\uD801\uDC00" to "\uD801\uDC28",
            // These letters are outside unicase 2.6.0's folding table.
            "\uA7C0\uA7C1" to "\uA7C0\uA7C1",
            "\uD800" to "\uD800",
        )) {
            assertEquals(expected, input.ankiCaseFold(), input)
            assertEquals(expected, expected.ankiCaseFold(), "idempotence: $input")
        }
    }
}
