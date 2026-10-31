// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import androidx.annotation.StringRes
import com.ichi2.anki.CommonString

/**
 * An explanation of how sorting occurs: (e.g. "High to low"; "Newest first")
 */
@StringRes
fun ColumnType.humanReadableExplanation(descending: Boolean): Int? =
    if (descending) {
        when (this) {
            ColumnType.TEXT -> CommonString.card_browser_order_subtitle_text_descending
            ColumnType.NUMERIC -> CommonString.card_browser_order_subtitle_numeric_descending
            ColumnType.DATE -> CommonString.card_browser_order_subtitle_date_descending
            ColumnType.UNSPECIFIED -> null
        }
    } else {
        when (this) {
            ColumnType.TEXT -> CommonString.card_browser_order_subtitle_text_ascending
            ColumnType.NUMERIC -> CommonString.card_browser_order_subtitle_numeric_ascending
            ColumnType.DATE -> CommonString.card_browser_order_subtitle_date_ascending
            ColumnType.UNSPECIFIED -> null
        }
    }
