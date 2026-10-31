// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.utils.ext

import anki.config.ConfigKey
import com.ichi2.anki.common.utils.ext.AddingDefaultsMode.DECIDE_BY_NOTE_TYPE
import com.ichi2.anki.common.utils.ext.AddingDefaultsMode.USE_CURRENT_DECK
import com.ichi2.anki.libanki.Config

/**
 * How the initial deck and note type are chosen when adding a note.
 *
 * @see USE_CURRENT_DECK
 * @see DECIDE_BY_NOTE_TYPE
 */
enum class AddingDefaultsMode {
    /** Start with the selected study deck and its remembered note type. */
    USE_CURRENT_DECK,

    /** Start with the current note type and its remembered destination deck. */
    DECIDE_BY_NOTE_TYPE,
}

/** How a new editor chooses its initial deck and note type. */
var Config.addingDefaultsMode: AddingDefaultsMode
    get() = if (getBool(ConfigKey.Bool.ADDING_DEFAULTS_TO_CURRENT_DECK)) USE_CURRENT_DECK else DECIDE_BY_NOTE_TYPE
    set(value) = setBool(ConfigKey.Bool.ADDING_DEFAULTS_TO_CURRENT_DECK, value == USE_CURRENT_DECK)
