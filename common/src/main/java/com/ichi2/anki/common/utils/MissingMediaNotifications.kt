// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.utils

import org.slf4j.LoggerFactory
import java.io.File

/** Limits missing-media notifications across card sides and ignores local API failures. */
class MissingMediaNotifications(
    private val localHost: String,
    private val apiPathPrefix: String,
    private val maxDisplayTimes: Int,
) {
    private var missingMediaCount = 0
    private var hasExecuted = false

    /** Only successful WebView notifications are remembered, bounded by [maxDisplayTimes]. */
    private val reportedMissingMedia = mutableSetOf<String>()

    fun processFailure(
        host: String?,
        path: String?,
        resolveFileName: () -> String,
        onFailure: (String) -> Unit,
    ) {
        // We do not want this to trigger more than once on the same side of the card as the UI will flicker.
        if (hasExecuted) return

        // The UX of the snackbar is annoying, as it obscures the content. Assume that if a user ignores it twice, they don't care.
        if (missingMediaCount >= maxDisplayTimes) return

        // We could do better here (external images failing due to no HTTPS), but failures can occur due to no network.
        // As we don't yet check the error data, we don't know.
        // Therefore limit this feature to the common case of local files, which should always work.
        if (host != localHost) return

        // API requests can fail during card replacement; they are not missing media files.
        if (path?.startsWith(apiPathPrefix) == true) {
            return
        }

        try {
            val filename = resolveFileName()

            // A duplicate must leave this side available to report a different missing file.
            if (filename in reportedMissingMedia) return

            hasExecuted = true
            onFailure.invoke(filename)
            reportedMissingMedia.add(filename)
            missingMediaCount++
        } catch (e: Exception) {
            logger.warn("Failed to notify UI of media failure", e)
        }
    }

    fun processMissingMedia(
        file: File,
        onFailure: (String) -> Unit,
    ) {
        // We want this to trigger more than once on the same side - as the user is in control of pressing "play"
        // and we want to provide feedback
        // The UX of the snackbar is annoying, as it obscures the content. Assume that if a user ignores it twice, they don't care.
        if (missingMediaCount >= maxDisplayTimes) return

        try {
            val fileName = file.name
            onFailure.invoke(fileName)
            if (!hasExecuted) {
                missingMediaCount++
            }
        } catch (e: Exception) {
            logger.warn("Failed to notify UI of media failure", e)
        } finally {
            hasExecuted = true
        }
    }

    fun onCardSideChange() {
        hasExecuted = false
    }

    private companion object {
        val logger = LoggerFactory.getLogger(MissingMediaNotifications::class.java)
    }
}
