// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.utils.ext

import anki.config.ConfigKey
import com.ichi2.anki.common.utils.configProperty
import com.ichi2.anki.libanki.Config
import com.ichi2.anki.model.CardsOrNotes

/** Whether the browser displays cards or notes. */
var Config.cardsOrNotes by configProperty(ConfigKey.Bool.BROWSER_TABLE_SHOW_NOTES_MODE).mapped(
    decode = { if (it) CardsOrNotes.NOTES else CardsOrNotes.CARDS },
    encode = { it == CardsOrNotes.NOTES },
)
