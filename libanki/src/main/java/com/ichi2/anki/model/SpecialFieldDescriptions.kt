// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.model

import com.ichi2.anki.Flag
import com.ichi2.anki.libanki.CardId

/** Facts shown as examples in the template editor's special-field picker. */
interface SpecialFieldDescriptionMetadata {
    val cardTemplateName: String
    val noteTypeName: String
    val tags: String?
    val flag: Int?
    val cardId: CardId?
    val deck: String?
    val subdeck: String?
}

/** Localized text required to describe special fields. */
enum class SpecialFieldText {
    EXAMPLE_SUFFIX,
    FRONT_SIDE_HELP,
    DECK_HELP,
    SUBDECK_HELP,
    FLAG_HELP,
    TAGS_HELP,
    CARD_ID_HELP,
    CARD_HELP,
    TYPE_HELP,
}

/** Builds description HTML while leaving resource lookup and styled rendering to the platform. */
fun SpecialField.descriptionHtml(
    metadata: SpecialFieldDescriptionMetadata,
    resolveText: (SpecialFieldText, List<String>) -> String,
): String {
    fun text(
        key: SpecialFieldText,
        vararg args: String,
    ): String = resolveText(key, args.toList())

    fun buildSuffix(value: String?): String {
        if (value == null) return ""
        return text(SpecialFieldText.EXAMPLE_SUFFIX, value)
    }
    return when (this) {
        SpecialFields.FrontSide -> text(SpecialFieldText.FRONT_SIDE_HELP)
        SpecialFields.Deck ->
            text(SpecialFieldText.DECK_HELP, buildSuffix(metadata.deck))

        SpecialFields.Subdeck ->
            text(SpecialFieldText.SUBDECK_HELP, buildSuffix(metadata.subdeck))
        SpecialFields.Flag -> {
            val code = metadata.flag ?: "N"
            text(
                SpecialFieldText.FLAG_HELP,
                if (code == "N") "flag$code" else "<b>flag$code</b>",
                "<b>$code</b>",
                Flag.entries.minOf { it.code }.toString(),
                Flag.entries.maxOf { it.code }.toString(),
            )
        }
        SpecialFields.Tags -> {
            val tags = if (metadata.tags.isNullOrBlank()) null else metadata.tags
            text(SpecialFieldText.TAGS_HELP, buildSuffix(tags))
        }
        SpecialFields.CardId ->
            text(SpecialFieldText.CARD_ID_HELP, buildSuffix(metadata.cardId?.toString()))

        SpecialFields.CardTemplate ->
            text(
                SpecialFieldText.CARD_HELP,
                buildSuffix(metadata.cardTemplateName),
            )

        SpecialFields.NoteType ->
            text(
                SpecialFieldText.TYPE_HELP,
                buildSuffix(metadata.noteTypeName),
            )
        // this shouldn't happen
        else -> ""
    }
}
