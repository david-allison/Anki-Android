// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Context
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.common.utils.ext.flagLabels
import com.ichi2.anki.common.utils.ext.getStringOrNull
import com.ichi2.anki.ui.internationalization.sentenceCase
import org.json.JSONObject

/** A unique ID representing this flag in a menu. */
@get:IdRes
val Flag.id: Int
    get() =
        when (this) {
            Flag.NONE -> R.id.flag_none
            Flag.RED -> R.id.flag_red
            Flag.ORANGE -> R.id.flag_orange
            Flag.GREEN -> R.id.flag_green
            Flag.BLUE -> R.id.flag_blue
            Flag.PINK -> R.id.flag_pink
            Flag.TURQUOISE -> R.id.flag_turquoise
            Flag.PURPLE -> R.id.flag_purple
        }

/** Drawable representing this flag. */
@get:DrawableRes
val Flag.drawableRes: Int
    get() =
        when (this) {
            Flag.NONE -> R.drawable.ic_flag_transparent
            Flag.RED -> R.drawable.ic_flag_red
            Flag.ORANGE -> R.drawable.ic_flag_orange
            Flag.GREEN -> R.drawable.ic_flag_green
            Flag.BLUE -> R.drawable.ic_flag_blue
            Flag.PINK -> R.drawable.ic_flag_pink
            Flag.TURQUOISE -> R.drawable.ic_flag_turquoise
            Flag.PURPLE -> R.drawable.ic_flag_purple
        }

/** Background color for cards with this flag in the browser. */
@get:ColorRes
val Flag.browserColorRes: Int?
    get() =
        when (this) {
            Flag.NONE -> null
            Flag.RED -> R.color.flag_red
            Flag.ORANGE -> R.color.flag_orange
            Flag.GREEN -> R.color.flag_green
            Flag.BLUE -> R.color.flag_blue
            Flag.PINK -> R.color.flag_pink
            Flag.TURQUOISE -> R.color.flag_turquoise
            Flag.PURPLE -> R.color.flag_purple
        }

/** Returns user-defined labels when available, otherwise translated names. */
suspend fun Flag.Companion.queryDisplayNames(context: Context): Map<Flag, String> {
    val labels = FlagLabels.loadFromColConfig()
    return Flag.entries.associateWith { flag ->
        if (flag == Flag.NONE) {
            flag.defaultDisplayName(context)
        } else {
            labels.getLabel(flag) ?: flag.defaultDisplayName(context)
        }
    }
}

private fun Flag.defaultDisplayName(context: Context): String =
    when (this) {
        Flag.NONE -> with(context) { TR.sentenceCase.noFlag }
        Flag.RED -> TR.actionsFlagRed()
        Flag.ORANGE -> TR.actionsFlagOrange()
        Flag.GREEN -> TR.actionsFlagGreen()
        Flag.BLUE -> TR.actionsFlagBlue()
        Flag.PINK -> TR.actionsFlagPink()
        Flag.TURQUOISE -> TR.actionsFlagTurquoise()
        Flag.PURPLE -> TR.actionsFlagPurple()
    }

/** Updates the user-defined label stored in the collection. */
suspend fun Flag.rename(newName: String) {
    FlagLabels.loadFromColConfig().updateName(this, newName)
}

/**
 * User-defined labels for flags. Stored in the collection optionally as `{ "1": "Redd" }`
 * [Flag.NONE] does not have a label
 */
@JvmInline
private value class FlagLabels(
    val value: JSONObject,
) {
    /**
     * @return the user-defined label for the provided flag, or null if undefined
     * This is not supported for [Flag.NONE] and is validated outside this method
     */
    fun getLabel(flag: Flag): String? = value.getStringOrNull(flag.code.toString())

    suspend fun updateName(
        flag: Flag,
        newName: String,
    ) {
        value.put(flag.code.toString(), newName)
        withCol {
            config.flagLabels = value
        }
    }

    companion object {
        suspend fun loadFromColConfig() = FlagLabels(withCol { config.flagLabels })
    }
}
