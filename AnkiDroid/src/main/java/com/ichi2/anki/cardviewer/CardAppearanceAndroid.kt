// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.cardviewer

import android.content.SharedPreferences
import com.ichi2.anki.reviewer.ReviewerCustomFonts
import com.ichi2.anki.settings.enums.DayTheme
import com.ichi2.anki.settings.enums.NightTheme
import com.ichi2.themes.Themes.currentTheme

/** Reads Android preferences and supplies the current theme to the CSS renderer. */
fun createCardAppearance(
    customFonts: ReviewerCustomFonts,
    preferences: SharedPreferences,
    fontScale: Float,
): CardAppearance =
    CardAppearance(
        customFonts,
        cardZoom = preferences.getInt("cardZoom", 100),
        imageZoom = preferences.getInt("imageZoom", 100),
        centerVertically = preferences.getBoolean("centerVertically", false),
        fontScale,
        themeClasses = ::currentThemeClasses,
    )

private fun currentThemeClasses(): String =
    buildString {
        if (currentTheme is NightTheme) {
            append(" night_mode nightMode")
            if (currentTheme == NightTheme.DARK) append(" ankidroid_dark_mode")
        } else if (currentTheme == DayTheme.PLAIN) {
            append(" ankidroid_plain_mode")
        }
    }
