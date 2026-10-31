// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.utils

import java.util.Locale

/** Selects an importer from normalized metadata without reading a content provider or copying files. */
object ImportFileClassifier {
    sealed class Resolution {
        sealed class ResolvedFile(
            open val fileName: String,
        ) : Resolution()

        data class DeckPackage(
            override val fileName: String,
        ) : ResolvedFile(fileName)

        data class CollectionPackage(
            override val fileName: String,
        ) : ResolvedFile(fileName)

        data class Text(
            override val fileName: String,
        ) : ResolvedFile(fileName)

        data class Failure(
            val reason: Reason,
            val fileName: String? = null,
        ) : Resolution()
    }

    enum class Reason { MISSING_FILENAME, ANKI_DATABASE, UNSUPPORTED_TYPE }

    fun resolve(
        fileName: String?,
        fileExtension: String,
        intentMimeType: String?,
        readProviderMimeType: () -> String?,
    ): Resolution {
        val providerMimeType by lazy(readProviderMimeType)
        val packageMimeType by lazy {
            val packageTypes = MimeTypeUtils.APKG_MIME_TYPES + MimeTypeUtils.COLPKG_MIME_TYPES
            intentMimeType.takeIf { it in packageTypes } ?: providerMimeType.takeIf { it in packageTypes }
        }
        val name =
            fileName ?: when {
                packageMimeType in MimeTypeUtils.COLPKG_MIME_TYPES -> "unknown_filename.colpkg"
                packageMimeType in MimeTypeUtils.APKG_MIME_TYPES || intentMimeType == "application/zip" -> "unknown_filename.apkg"
                else -> return Resolution.Failure(Reason.MISSING_FILENAME)
            }
        return when {
            // Package filenames and specific package MIME types take precedence over text hints.
            isCollectionPackage(name) -> Resolution.CollectionPackage(name)
            isDeckPackage(name) -> Resolution.DeckPackage(name)
            packageMimeType in MimeTypeUtils.COLPKG_MIME_TYPES -> Resolution.CollectionPackage(name)
            packageMimeType in MimeTypeUtils.APKG_MIME_TYPES -> Resolution.DeckPackage(name)
            fileExtension.lowercase(Locale.ROOT) in listOf("csv", "tsv", "txt") ||
                intentMimeType in MimeTypeUtils.CSV_TSV_MIME_TYPES || providerMimeType in MimeTypeUtils.CSV_TSV_MIME_TYPES ->
                Resolution.Text(name)
            hasExtension(name, "anki2") -> Resolution.Failure(Reason.ANKI_DATABASE, name)
            else -> Resolution.Failure(Reason.UNSUPPORTED_TYPE, name)
        }
    }

    fun isCollectionPackage(filename: String?): Boolean =
        filename != null && (filename.lowercase(Locale.ROOT).endsWith(".colpkg") || "collection.apkg" == filename)

    fun isDeckPackage(filename: String?): Boolean =
        filename != null && filename.lowercase(Locale.ROOT).endsWith(".apkg") && "collection.apkg" != filename

    fun isValidPackageName(filename: String?): Boolean = isDeckPackage(filename) || isCollectionPackage(filename)

    fun hasExtension(
        filename: String,
        extension: String?,
    ): Boolean {
        val fileParts = filename.split("\\.".toRegex()).toTypedArray()
        if (fileParts.size < 2) return false
        return fileParts.last().lowercase(Locale.ROOT).startsWith(extension!!)
    }
}
