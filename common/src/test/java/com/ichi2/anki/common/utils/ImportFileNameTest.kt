// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.utils

import com.ichi2.anki.common.time.MockTime
import com.ichi2.anki.common.time.TimeManager
import org.junit.Test
import java.net.URLEncoder
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImportFileNameTest {
    @Test
    fun `long Unicode names stay within the encoded filename limit`() {
        val input = "好".repeat(44) + ".apkg"
        val actual = URLEncoder.encode(ImportFileName.normalize(input) { "apkg" }, "UTF-8")

        assertTrue(actual.endsWith(".apkg"))
        assertContains(actual, "...")
        assertContains(actual, "%E5%A5%BD")
        assertTrue(actual.substringAfter("%E5%A5%BD").length <= 100)
    }

    @Test
    fun `leading dots on real filenames are preserved`() {
        for (name in listOf(".hidden.apkg", "..apkg")) {
            assertEquals(name, ImportFileName.normalize(name) { "apkg" })
        }
    }

    @Test
    fun `empty dot and dot-dot names use the unnamed fallback`() {
        for (name in listOf("", ".", "..")) {
            assertEquals("unnamed_file", ImportFileName.normalize(name) { "" })
        }
    }

    @Test
    fun `a partial encoded character uses the timestamp fallback`() {
        val originalTime = TimeManager.time
        try {
            TimeManager.resetWith(MockTime(1_600_000_000_000))
            val input = "a".repeat(89) + "ääääääääää.apkg"

            assertEquals("file_1600000000000.apkg", ImportFileName.normalize(input) { "apkg" })
        } finally {
            TimeManager.resetWith(originalTime)
        }
    }
}
