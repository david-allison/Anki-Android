// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Mani <infinyte01@gmail.com>

package com.ichi2.anki.jsaddons

object AddonsConst {
    const val ANKIDROID_JS_ADDON_KEYWORDS = "ankidroid-js-addon"
    const val REVIEWER_ADDON = "reviewer"
    const val NOTE_EDITOR_ADDON = "note-editor"
}

/**
 * Identifiers for the WebView pages an addon can target, used in the manifest's `pages` list.
 *
 * These are stable ids, not asset paths: unknown ids are tolerated (an addon may target a page
 * a newer AnkiDroid adds). The reviewer is included so one mechanism serves every page.
 */
object AddonPages {
    const val REVIEWER = "reviewer"
    const val DECK_OPTIONS = "deck-options"
    const val STATISTICS = "statistics"
    const val CARD_INFO = "card-info"
    const val CONGRATS = "congrats"
    const val CHANGE_NOTETYPE = "change-notetype"
    const val IMPORT = "import"

    /** Maps a [com.ichi2.anki.pages.PageFragment.pagePath] to a page id, or null if not addon-hostable */
    fun fromPagePath(pagePath: String): String? =
        when (pagePath.substringBefore("/").substringBefore("#")) {
            "graphs" -> STATISTICS
            "deck-options" -> DECK_OPTIONS
            "card-info" -> CARD_INFO
            "congrats" -> CONGRATS
            "change-notetype" -> CHANGE_NOTETYPE
            "import-anki-package", "import-csv", "import-page" -> IMPORT
            else -> null
        }
}
