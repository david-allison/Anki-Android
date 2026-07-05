/*
 * Copyright (c) 2020 Mani infinyte01@gmail.com
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see http://www.gnu.org/licenses/>.
 *
 */

package com.ichi2.anki

import com.github.zafarkhaja.semver.Version
import com.ichi2.anki.cardviewer.ViewerCommand

object AnkiDroidJsAPIConstants {
    // JS API ERROR CODE
    const val ANKI_JS_ERROR_CODE_ERROR: Int = -1
    const val ANKI_JS_ERROR_CODE_DEFAULT: Int = 0
    const val ANKI_JS_ERROR_CODE_MARK_CARD: Int = 1
    const val ANKI_JS_ERROR_CODE_FLAG_CARD: Int = 2

    const val ANKI_JS_ERROR_CODE_BURY_CARD: Int = 3
    const val ANKI_JS_ERROR_CODE_SUSPEND_CARD: Int = 4
    const val ANKI_JS_ERROR_CODE_BURT_NOTE: Int = 5
    const val ANKI_JS_ERROR_CODE_SUSPEND_NOTE: Int = 6
    const val ANKI_JS_ERROR_CODE_SET_DUE: Int = 7
    const val ANKI_JS_ERROR_CODE_SEARCH_CARD: Int = 8

    // js api developer contact
    const val CURRENT_JS_API_VERSION = "0.0.3"
    const val MINIMUM_JS_API_VERSION = "0.0.3"

    /** Whether code built against a given JS API version can run on this AnkiDroid */
    enum class ApiCompatibility {
        SUPPORTED,

        /** older than [MINIMUM_JS_API_VERSION]: the addon needs updating */
        ADDON_TOO_OLD,

        /** newer than [CURRENT_JS_API_VERSION]: AnkiDroid needs updating */
        REQUIRES_NEWER_APP,

        /** not a parseable semantic version */
        INVALID,
    }

    /**
     * Checks a supplied JS API version against the range this AnkiDroid supports:
     * [MINIMUM_JS_API_VERSION]..[CURRENT_JS_API_VERSION]
     *
     * Addons supply the single version they were developed against rather than a range:
     * keeping the supported range on the app side lets a future AnkiDroid widen it
     * without every published addon needing an update
     *
     * Matches the policy of AnkiDroidJsAPI.requireApiVersion
     */
    fun checkApiVersion(supplied: String): ApiCompatibility {
        val suppliedVersion =
            try {
                Version.parse(supplied)
            } catch (_: Exception) {
                return ApiCompatibility.INVALID
            }
        return when {
            suppliedVersion.isHigherThan(Version.parse(CURRENT_JS_API_VERSION)) -> ApiCompatibility.REQUIRES_NEWER_APP
            suppliedVersion.isLowerThan(Version.parse(MINIMUM_JS_API_VERSION)) -> ApiCompatibility.ADDON_TOO_OLD
            else -> ApiCompatibility.SUPPORTED
        }
    }

    val flagCommands =
        mapOf(
            "none" to ViewerCommand.UNSET_FLAG,
            "red" to ViewerCommand.TOGGLE_FLAG_RED,
            "orange" to ViewerCommand.TOGGLE_FLAG_ORANGE,
            "green" to ViewerCommand.TOGGLE_FLAG_GREEN,
            "blue" to ViewerCommand.TOGGLE_FLAG_BLUE,
            "pink" to ViewerCommand.TOGGLE_FLAG_PINK,
            "turquoise" to ViewerCommand.TOGGLE_FLAG_TURQUOISE,
            "purple" to ViewerCommand.TOGGLE_FLAG_PURPLE,
        )
}
