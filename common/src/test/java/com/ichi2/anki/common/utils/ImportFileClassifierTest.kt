// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.assertIs
import com.ichi2.anki.common.utils.ImportFileClassifier.Resolution as ImportResolution

class ImportFileClassifierTest {
    private val packageMimeTypes =
        mapOf(
            "application/apkg" to ImportResolution.DeckPackage::class,
            "application/x-apkg" to ImportResolution.DeckPackage::class,
            "application/vnd.anki" to ImportResolution.DeckPackage::class,
            "application/colpkg" to ImportResolution.CollectionPackage::class,
            "application/x-colpkg" to ImportResolution.CollectionPackage::class,
        )

    @Test
    fun `specific package MIME types take precedence over text metadata`() {
        for ((packageMime, expectedType) in packageMimeTypes) {
            for (filename in listOf("notes.csv", "notes.tsv", "notes.txt", "download", "download.zip", "")) {
                for ((intentMime, providerMime) in listOf(
                    packageMime to "text/plain",
                    "text/plain" to packageMime,
                    packageMime to "text/csv",
                    "text/csv" to packageMime,
                    null to packageMime,
                    "application/octet-stream" to packageMime,
                )) {
                    val resolution = resolveWithMetadata(filename, intentMime, providerMime)

                    assertEquals("$filename, intent=$intentMime, provider=$providerMime", expectedType, resolution::class)
                }
            }
        }
    }

    @Test
    fun `specific package MIME types provide a fallback filename`() {
        for ((packageMime, expectedType) in packageMimeTypes) {
            for ((intentMime, providerMime) in listOf(packageMime to "text/plain", "text/plain" to packageMime, null to packageMime)) {
                val resolution = assertIs<ImportResolution.ResolvedFile>(resolveWithMetadata(null, intentMime, providerMime))

                assertEquals(expectedType, resolution::class)
                val extension = if (expectedType == ImportResolution.CollectionPackage::class) "colpkg" else "apkg"
                assertEquals("unknown_filename.$extension", resolution.fileName)
            }
        }
    }

    @Test
    fun `package filenames determine the package kind before MIME hints`() {
        for ((filename, expectedType) in listOf(
            "deck.apkg" to ImportResolution.DeckPackage::class,
            "legacy.APKG" to ImportResolution.DeckPackage::class,
            "backup.colpkg" to ImportResolution.CollectionPackage::class,
            "collection.apkg" to ImportResolution.CollectionPackage::class,
        )) {
            for (packageMime in packageMimeTypes.keys) {
                assertEquals(expectedType, resolveWithMetadata(filename, packageMime, packageMime)::class)
            }
        }
    }

    @Test
    fun `intent package MIME wins when the provider supplies another package MIME`() {
        assertIs<ImportResolution.DeckPackage>(resolveWithMetadata("download", "application/apkg", "application/colpkg"))
        assertIs<ImportResolution.CollectionPackage>(resolveWithMetadata("download", "application/colpkg", "application/apkg"))
    }

    @Test
    fun `generic MIME types do not identify named files as Anki packages`() {
        for (mime in listOf("application/octet-stream", "application/zip", "text/plain")) {
            assertIs<ImportResolution.Text>(resolveWithMetadata("notes.csv", mime, mime))
        }
        for (mime in listOf("application/octet-stream", "application/zip")) {
            assertIs<ImportResolution.Failure>(resolveWithMetadata("download", mime, mime))
        }
        assertIs<ImportResolution.Text>(resolveWithMetadata("download", "text/plain", "text/plain"))
    }

    @Test
    fun `nameless ZIP fallback defers to a specific package MIME`() {
        assertIs<ImportResolution.DeckPackage>(resolveWithMetadata(null, "application/zip", null))
        val collection =
            assertIs<ImportResolution.CollectionPackage>(resolveWithMetadata(null, "application/zip", "application/colpkg"))
        assertEquals("unknown_filename.colpkg", collection.fileName)
    }

    @Test
    fun collectionApkgIsValid() {
        assertTrue(ImportFileClassifier.isValidPackageName("collection.apkg"))
    }

    @Test
    fun collectionColPkgIsValid() {
        assertTrue(ImportFileClassifier.isValidPackageName("collection.colpkg"))
    }

    @Test
    fun deckApkgIsValid() {
        assertTrue(ImportFileClassifier.isValidPackageName("deckName.apkg"))
    }

    @Test
    fun deckColPkgIsValid() {
        assertTrue(ImportFileClassifier.isValidPackageName("deckName.colpkg"))
    }

    @Test
    fun nullIsNotValidPackage() {
        assertFalse(ImportFileClassifier.isValidPackageName(null))
    }

    @Test
    fun docxIsNotValidForImport() {
        assertFalse(ImportFileClassifier.isValidPackageName("test.docx"))
    }

    @Test
    fun onlySupportedTextMimeTypesSelectTheTextImporter() {
        val validMimeTypes =
            listOf(
                "text/plain",
                "text/comma-separated-values",
                "text/tab-separated-values",
                "text/csv",
                "text/tsv",
            )
        val invalidMimeTypes =
            listOf(
                null,
                "text/html",
                "application/pdf",
                "image/jpeg",
                "image/png",
            )

        for (mime in validMimeTypes) {
            val resolution = resolveWithMetadata("notes", null, mime)
            assertIs<ImportResolution.Text>(resolution, "Expected text MIME to be accepted: $mime")
        }

        for (mime in invalidMimeTypes) {
            val resolution = resolveWithMetadata("notes", null, mime)
            assertIs<ImportResolution.Failure>(resolution, "Expected unsupported MIME to be rejected: $mime")
        }
    }

    private fun resolveWithMetadata(
        filename: String?,
        intentMime: String?,
        providerMime: String?,
    ): ImportResolution {
        val normalizedName = filename?.ifEmpty { "unnamed_file" }
        return ImportFileClassifier.resolve(
            fileName = normalizedName,
            fileExtension = normalizedName?.substringAfterLast('.', "").orEmpty(),
            intentMimeType = intentMime,
            readProviderMimeType = { providerMime },
        )
    }
}
