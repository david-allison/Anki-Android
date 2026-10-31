// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import androidx.annotation.VisibleForTesting
import org.slf4j.LoggerFactory

/**
 * Text copied to the clipboard cannot be unbounded. To ensure callers are aware
 * that provided text may be truncated, we require that they pass an explicit instance of this value class.
 */
@JvmInline
value class TruncatedString private constructor(
    val value: String,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(TruncatedString::class.java)

        /**
         * Text in the clipboard cannot exceed 1 MB because that is the limit of the Binder IPC transaction buffer.
         * We truncate at a much lower limit because the 1 MB limit is shared between all ongoing transactions.
         */
        @VisibleForTesting
        const val MAX_CLIPBOARD_TEXT_LENGTH = 100_000

        /**
         * If [text] exceeds the maximum length ([MAX_CLIPBOARD_TEXT_LENGTH]), it is truncated and a warning is logged. Does not throw.
         *
         * TODO: Low-priority, only affects last character in the rare case of a truncation: emojis, graphemes etc. may be split in half; should split on valid boundary
         */
        fun from(text: String): TruncatedString {
            if (text.length > MAX_CLIPBOARD_TEXT_LENGTH) {
                logger.warn("Text length (${text.length}) exceeds maximum clipboard length ($MAX_CLIPBOARD_TEXT_LENGTH). Truncating.")
            }
            return TruncatedString(text.take(MAX_CLIPBOARD_TEXT_LENGTH))
        }
    }
}
