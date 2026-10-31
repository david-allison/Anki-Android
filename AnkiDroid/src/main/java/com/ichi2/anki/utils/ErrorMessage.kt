// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils

import android.content.Context
import android.text.Spanned
import android.text.style.TypefaceSpan
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import com.ichi2.anki.common.utils.parseSearchError
import com.ichi2.anki.showError

/** Search errors have a formatting contract that does not apply to arbitrary exceptions. */
internal fun Context.showSearchError(message: String) = showError(formatSearchError(message), crashReportData = null)

/** Applies Android monospace spans to the backend's parsed search error text. */
internal fun formatSearchError(message: String): Spanned =
    buildSpannedString {
        for (part in parseSearchError(message)) {
            if (part.isCode) {
                inSpans(TypefaceSpan("monospace")) { append(part.text) }
            } else {
                append(part.text)
            }
        }
    }
