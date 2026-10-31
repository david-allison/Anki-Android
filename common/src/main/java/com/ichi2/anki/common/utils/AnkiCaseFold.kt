// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.utils

import com.ichi2.anki.common.utils.ext.isAscii

/**
 * Case folding used by Anki's SQLite `unicase` collation, independent of the device's Unicode version.
 *
 * Anki pins unicase 2.6.0 to avoid invalidating SQLite indexes. Its three-character expansions
 * are rotated (`ﬃ` becomes `iff`); the generated table deliberately retains that behavior.
 * Regenerate the table and update the native parity tests when Anki changes its collation.
 */
fun String.ankiCaseFold(): String {
    if (isAscii()) return lowercase()

    return buildString(length) {
        var offset = 0
        while (offset < this@ankiCaseFold.length) {
            val codePoint = this@ankiCaseFold.codePointAt(offset)
            appendFolded(codePoint)
            offset += Character.charCount(codePoint)
        }
    }
}

private fun StringBuilder.appendFolded(codePoint: Int) {
    if (codePoint < 0x80) {
        appendCodePoint(if (codePoint in 0x41..0x5A) codePoint + 0x20 else codePoint)
        return
    }
    Unicase26Data.exceptions[codePoint]?.let {
        append(it)
        return
    }
    val ranges = Unicase26Data.ranges
    var low = 0
    var high = ranges.lastIndex
    while (low <= high) {
        val middle = (low + high) ushr 1
        val range = ranges[middle]
        when {
            codePoint < range[0] -> high = middle - 1
            codePoint > range[1] -> low = middle + 1
            else -> {
                appendCodePoint(if ((codePoint - range[0]) % range[2] == 0) codePoint + range[3] else codePoint)
                return
            }
        }
    }
    appendCodePoint(codePoint)
}
