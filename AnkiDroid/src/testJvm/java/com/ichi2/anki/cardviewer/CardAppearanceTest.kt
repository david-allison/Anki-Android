// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.cardviewer

import androidx.core.content.edit
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.ichi2.anki.reviewer.ReviewerCustomFonts
import com.ichi2.anki.settings.enums.DayTheme
import com.ichi2.anki.settings.enums.NightTheme
import com.ichi2.themes.Themes
import org.junit.Test
import kotlin.test.assertEquals

class CardAppearanceTest {
    @Test
    fun `an existing appearance follows theme changes while retaining its card classes`() {
        val preferences = SPMockBuilder().createSharedPreferences().apply { edit { putBoolean("centerVertically", true) } }
        val appearance = createCardAppearance(ReviewerCustomFonts(), preferences, fontScale = 1f)
        val previous = Themes.currentTheme
        try {
            Themes.currentTheme = DayTheme.LIGHT
            assertEquals("card card2 vertically_centered", appearance.getCardClass(2))
            Themes.currentTheme = DayTheme.PLAIN
            assertEquals("card card2 vertically_centered ankidroid_plain_mode", appearance.getCardClass(2))
            for (theme in NightTheme.entries) {
                Themes.currentTheme = theme
                val darkClass = if (theme == NightTheme.DARK) " ankidroid_dark_mode" else ""
                assertEquals("card card2 vertically_centered night_mode nightMode$darkClass", appearance.getCardClass(2))
            }
        } finally {
            Themes.currentTheme = previous
        }
    }
}
