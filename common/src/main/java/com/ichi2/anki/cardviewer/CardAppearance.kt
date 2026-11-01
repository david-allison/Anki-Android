// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.cardviewer

import androidx.annotation.CheckResult
import com.ichi2.anki.reviewer.ReviewerCustomFonts

/** Responsible for calculating CSS and element styles and modifying content on a flashcard  */
class CardAppearance(
    private val customFonts: ReviewerCustomFonts,
    private val cardZoom: Int,
    private val imageZoom: Int,
    private val centerVertically: Boolean,
    private val fontScale: Float,
    private val themeClasses: () -> String,
) {
    /** Below could be in a better abstraction.  */
    fun appendCssStyle(style: StringBuilder) {
        // Preserve the system font size while scaling text and em-based layout together.
        if (fontScale != 1f) {
            style.append("html { zoom: $fontScale }\n")
        }

        // Zoom cards
        if (cardZoom != 100) {
            style.append("body { zoom: ${cardZoom / 100.0} }\n")
        }

        // Zoom images
        if (imageZoom != 100) {
            style.append("img { zoom: ${imageZoom / 100.0} }\n")
        }
    }

    @CheckResult
    fun getCssClasses(): String =
        buildString {
            if (centerVertically) append(" vertically_centered")
            // Resolve the theme when rendering so an existing appearance follows theme changes.
            append(themeClasses())
        }

    val style: String
        get() {
            val style = StringBuilder()
            customFonts.updateCssStyle(style)
            appendCssStyle(style)
            return style.toString()
        }

    fun getCardClass(oneBasedCardOrdinal: Int): String = "card card$oneBasedCardOrdinal" + getCssClasses()

    companion object {
        private val nightModeClassRegex = Regex("\\.night(?:_m|M)ode\\b")

        fun fixBoldStyle(content: String): String {
            // In order to display the bold style correctly, we have to change
            // font-weight to 700
            return content.replace("font-weight:600;", "font-weight:700;")
        }
    }
}
