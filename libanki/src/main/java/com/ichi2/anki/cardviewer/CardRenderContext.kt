// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.cardviewer

import androidx.annotation.CheckResult
import com.ichi2.anki.common.utils.ext.hideAudioPlayButtons
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.CardOrdinal
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.SoundOrVideoTag
import com.ichi2.anki.libanki.TemplateManager.TemplateRenderContext.TemplateRenderOutput
import com.ichi2.anki.libanki.template.MathJax
import com.ichi2.anki.multimedia.expandSounds
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Holds the card appearance and collection settings used to render HTML
 *
 * @see renderCard
 */
class CardRenderContext(
    private val typeAnswer: TypeAnswer,
    private val cardAppearance: CardAppearance,
    private val cardTemplate: CardTemplate,
    private val showAudioPlayButtons: Boolean,
    private val compareAnswer: (String, String, Boolean) -> String,
    private val classifyMedia: (SoundOrVideoTag, File) -> SoundOrVideoTag.Type,
) {
    /**
     * Renders card content and appearance settings to produce a [RenderedCard]
     */
    @CheckResult
    fun renderCard(
        col: Collection,
        card: Card,
        side: SingleCardSide,
    ): RenderedCard {
        // obtain the libAnki-rendered card
        var content: String = if (side == SingleCardSide.FRONT) card.question(col) else card.answer(col)
        // IRI-encodes media: `foo bar` -> `foo%20bar`
        content = col.media.escapeMediaFilenames(content)
        // produces either an <input> or <span>...</span> to denote typed input
        content = filterTypeAnswer(content, side)
        // wraps content in <div id="qa">
        content = enrichWithQADiv(content)
        // expands [anki:q:1] to a play button
        content = expandSounds(content, card.renderOutput(col), col)
        // fixes an Android bug where font-weight:600 does not display
        content = CardAppearance.fixBoldStyle(content)

        // based on the content, load appropriate scripts such as MathJax, then render
        return render(content, card.ord)
    }

    private fun render(
        content: String,
        ord: CardOrdinal,
    ): RenderedCard {
        val requiresMathjax = MathJax.textContainsMathjax(content)

        val style = cardAppearance.style
        val script =
            when (requiresMathjax) {
                false -> ""
                true ->
                    """        <script src="file:///android_asset/backend/js/mathjax.js"></script>
        <script src="file:///android_asset/backend/js/vendor/mathjax/tex-chtml-full.js"></script>"""
            }
        val cardClass = cardAppearance.getCardClass(ord + 1) + if (requiresMathjax) " mathjax-needs-to-render" else ""

        logger.trace("content card = \n {}", content)
        logger.trace("::style:: / {}", style)

        return cardTemplate.render(content, style, script, cardClass)
    }

    /**
     * Adds a div html tag around the contents to have an indication, where answer/question is displayed
     *
     * @param content The content to surround with tags.
     * @return The enriched content
     */
    private fun enrichWithQADiv(content: String) =
        buildString {
            append("""<div id="qa">""")
            append(content)
            append("</div>")
        }

    private fun filterTypeAnswer(
        content: String,
        side: SingleCardSide,
    ): String =
        when (side) {
            SingleCardSide.FRONT -> typeAnswer.filterQuestion(content)
            SingleCardSide.BACK -> typeAnswer.filterAnswer(content, compareAnswer)
        }

    private fun expandSounds(
        content: String,
        renderOutput: TemplateRenderOutput,
        col: Collection,
    ): String {
        val mediaDir = col.media.dir

        return expandSounds(
            content,
            renderOutput,
            showAudioPlayButtons,
            mediaDir,
            classifyMedia,
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CardRenderContext::class.java)

        fun create(
            col: Collection,
            typeAnswer: TypeAnswer,
            cardAppearance: CardAppearance,
            cardTemplate: CardTemplate,
            compareAnswer: (String, String, Boolean) -> String,
            classifyMedia: (SoundOrVideoTag, File) -> SoundOrVideoTag.Type,
        ): CardRenderContext =
            CardRenderContext(
                typeAnswer,
                cardAppearance,
                cardTemplate,
                showAudioPlayButtons = !col.config.hideAudioPlayButtons,
                compareAnswer,
                classifyMedia,
            )
    }
}
