// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.cardviewer

import androidx.annotation.CheckResult

/**
 * Full HTML to be displayed in a WebView
 */
@JvmInline
value class RenderedCard(
    val html: String,
)

/**
 * The static HTML template for rendering a card.
 *
 * Replaces the style, script, class, and content sections.
 */
class CardTemplate(
    template: String,
) {
    private var preStyle: String? = null
    private var preScript: String? = null
    private var preClass: String? = null
    private var preContent: String? = null
    private var postContent: String? = null

    @CheckResult
    fun render(
        content: String,
        style: String,
        script: String,
        cardClass: String,
    ): RenderedCard {
        val html = preStyle + style + preScript + script + preClass + cardClass + preContent + content + postContent
        return RenderedCard(html)
    }

    init {
        // Note: This refactoring means the template must be in the specific order of style, class, content.
        // Since this is a const loaded from an asset file, I'm fine with this.
        val classDelim = "::class::"
        val styleDelim = "::style::"
        val scriptDelim = "::script::"
        val contentDelim = "::content::"
        val styleIndex = template.indexOf(styleDelim)
        val scriptIndex = template.indexOf(scriptDelim)
        val classIndex = template.indexOf(classDelim)
        val contentIndex = template.indexOf(contentDelim)
        try {
            preStyle = template.take(styleIndex)
            preScript = template.substring(styleIndex + styleDelim.length, scriptIndex)
            preClass = template.substring(scriptIndex + scriptDelim.length, classIndex)
            preContent = template.substring(classIndex + classDelim.length, contentIndex)
            postContent = template.substring(contentIndex + contentDelim.length)
        } catch (ex: StringIndexOutOfBoundsException) {
            throw IllegalStateException("The card template had replacement string order, or content changed", ex)
        }
    }
}
