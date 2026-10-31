// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import com.ichi2.utils.ImportUtils.FileImporter.CacheFileResult
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

class ImportSourceCacheTest {
    @get:Rule
    val directory = TemporaryFolder()

    private fun cache(openSource: () -> InputStream?): CacheFileResult =
        cacheImportSource(
            File(directory.root, "import.apkg").path,
            openSource = openSource,
            copy = { input, path -> File(path).outputStream().use { input.copyTo(it) } },
        )

    @Test
    fun `unexpected source errors are still reported`() {
        val exception = IOException("Provider failed")

        val result = cache { throw exception }

        val details = assertIs<CacheFileResult.Error>(result).asErrorDetails()!!
        assertSame(exception, details.userFacingException)
        assertSame(exception, details.exceptionForReport)
    }

    @Test
    fun `pending or trashed download returns an import failure without a crash report`() {
        val exception = IllegalStateException("Only owner is able to interact with pending/trashed item")

        val result = cache { throw exception }

        val details = assertIs<CacheFileResult.SourceFileUnavailable>(result).asErrorDetails()!!
        assertSame(exception, details.userFacingException)
        assertNull(details.exceptionForReport)
    }

    @Test
    fun `other illegal state errors from the source are still reported`() {
        val exception = IllegalStateException("Provider failed")

        val result = cache { throw exception }

        val details = assertIs<CacheFileResult.Error>(result).asErrorDetails()!!
        assertSame(exception, details.userFacingException)
        assertSame(exception, details.exceptionForReport)
    }

    @Test
    fun `file not found while reading an open source is still reported`() {
        val exception = FileNotFoundException("Read failed after opening")
        var closed = false
        val result =
            cache {
                object : InputStream() {
                    override fun read(): Int = throw exception

                    override fun close() {
                        closed = true
                    }
                }
            }

        val details = assertIs<CacheFileResult.Error>(result).asErrorDetails()!!
        assertSame(exception, details.userFacingException)
        assertSame(exception, details.exceptionForReport)
        assertEquals(true, closed, "the source must be closed on copy failure")
    }
}
