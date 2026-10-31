// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.cardviewer

import com.ichi2.anki.Flag

/** The resulting flag for a flag command, or null when this command does not change flags. */
fun ViewerCommand.flagAfter(currentFlag: Flag?): Flag? {
    val requested =
        when (this) {
            ViewerCommand.TOGGLE_FLAG_RED -> Flag.RED
            ViewerCommand.TOGGLE_FLAG_ORANGE -> Flag.ORANGE
            ViewerCommand.TOGGLE_FLAG_GREEN -> Flag.GREEN
            ViewerCommand.TOGGLE_FLAG_BLUE -> Flag.BLUE
            ViewerCommand.TOGGLE_FLAG_PINK -> Flag.PINK
            ViewerCommand.TOGGLE_FLAG_TURQUOISE -> Flag.TURQUOISE
            ViewerCommand.TOGGLE_FLAG_PURPLE -> Flag.PURPLE
            ViewerCommand.UNSET_FLAG -> return Flag.NONE
            else -> return null
        }
    // Toggling requires a current card; unsetting may be dispatched without one.
    return if (requested == currentFlag!!) Flag.NONE else requested
}
