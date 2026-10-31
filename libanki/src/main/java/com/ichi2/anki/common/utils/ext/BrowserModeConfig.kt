// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.utils.ext

import anki.config.ConfigKey
import com.ichi2.anki.libanki.Config
import com.ichi2.anki.model.CardsOrNotes

/** Whether the browser displays cards or notes. */
var Config.cardsOrNotes: CardsOrNotes
    get() = if (getBool(ConfigKey.Bool.BROWSER_TABLE_SHOW_NOTES_MODE)) CardsOrNotes.NOTES else CardsOrNotes.CARDS
    set(value) = setBool(ConfigKey.Bool.BROWSER_TABLE_SHOW_NOTES_MODE, value == CardsOrNotes.NOTES)
