// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>

package com.ichi2.anki.common.utils

import com.ichi2.anki.common.time.TimeManager
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder

/** Produces a basename that remains usable when import paths are URL-encoded. */
object ImportFileName {
    private const val FILE_NAME_SHORTENING_THRESHOLD = 100
    private val logger = LoggerFactory.getLogger(ImportFileName::class.java)

    /**
     * Uses the last path segment ([File.name]) so directory components cannot escape the
     * destination. Empty names, `.`, and `..` become `unnamed_file`. Leading dots on a real
     * filename are preserved (e.g. `.hidden.apkg`, `..apkg`).
     */
    private fun sanitizeFileName(fileName: String): String {
        val sanitized = File(fileName).name
        return if (sanitized.isEmpty() || sanitized == "." || sanitized == "..") {
            "unnamed_file"
        } else {
            sanitized
        }
    }

    fun normalize(
        fileName: String,
        extension: () -> String,
    ): String {
        // #6137 - filenames can be too long when URLEncoded
        val sanitized = sanitizeFileName(fileName)
        return try {
            val encoded = URLEncoder.encode(sanitized, "UTF-8")
            if (encoded.length <= FILE_NAME_SHORTENING_THRESHOLD) {
                logger.debug("No filename truncation necessary")
                sanitized
            } else {
                logger.debug("Filename was longer than {}, shortening", FILE_NAME_SHORTENING_THRESHOLD)
                // take 90 instead of 100 so we don't get the extension
                val substringLength = FILE_NAME_SHORTENING_THRESHOLD - 10
                val shortenedFileName = encoded.take(substringLength) + "..." + extension()
                logger.debug("Shortened filename")
                // if we don't decode, % is double-encoded
                sanitizeFileName(URLDecoder.decode(shortenedFileName, "UTF-8"))
            }
        } catch (e: Exception) {
            logger.warn("Failed to shorten file", e)
            sanitizeFileName("file_${TimeManager.time.intTimeMS()}.${extension()}")
        }
    }
}
