// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpecialFieldDescriptionsTest {
    private data class Metadata(
        override val cardTemplateName: String = "Card 2",
        override val noteTypeName: String = "Basic",
        override val tags: String? = "a b",
        override val flag: Int? = 3,
        override val cardId: Long? = 1767778189,
        override val deck: String? = "Parent::Child",
        override val subdeck: String? = "Child",
    ) : SpecialFieldDescriptionMetadata

    private data class TextCall(
        val key: SpecialFieldText,
        val args: List<String>,
    )

    private fun calls(
        field: SpecialField,
        metadata: Metadata = Metadata(),
    ): List<TextCall> {
        val calls = mutableListOf<TextCall>()
        field.descriptionHtml(metadata) { key, args ->
            calls += TextCall(key, args)
            "resolved-$key"
        }
        return calls
    }

    @Test
    fun `field examples are passed to the suffix and the correct description`() {
        val examples =
            listOf(
                Triple(SpecialFields.Deck, SpecialFieldText.DECK_HELP, "Parent::Child"),
                Triple(SpecialFields.Subdeck, SpecialFieldText.SUBDECK_HELP, "Child"),
                Triple(SpecialFields.Tags, SpecialFieldText.TAGS_HELP, "a b"),
                Triple(SpecialFields.CardId, SpecialFieldText.CARD_ID_HELP, "1767778189"),
                Triple(SpecialFields.CardTemplate, SpecialFieldText.CARD_HELP, "Card 2"),
                Triple(SpecialFields.NoteType, SpecialFieldText.TYPE_HELP, "Basic"),
            )
        for ((field, key, example) in examples) {
            assertEquals(
                listOf(TextCall(SpecialFieldText.EXAMPLE_SUFFIX, listOf(example)), TextCall(key, listOf("resolved-EXAMPLE_SUFFIX"))),
                calls(field),
                field.name,
            )
        }
    }

    @Test
    fun `absent examples do not generate a suffix`() {
        val metadata = Metadata(tags = null, cardId = null, deck = null, subdeck = null)
        for ((field, key) in listOf(
            SpecialFields.Deck to SpecialFieldText.DECK_HELP,
            SpecialFields.Subdeck to SpecialFieldText.SUBDECK_HELP,
            SpecialFields.Tags to SpecialFieldText.TAGS_HELP,
            SpecialFields.CardId to SpecialFieldText.CARD_ID_HELP,
        )) {
            assertEquals(listOf(TextCall(key, listOf(""))), calls(field, metadata))
        }
        assertEquals(listOf(TextCall(SpecialFieldText.TAGS_HELP, listOf(""))), calls(SpecialFields.Tags, Metadata(tags = " ")))
    }

    @Test
    fun `flag examples preserve ASCII codes and bold only known values`() {
        assertEquals(listOf(TextCall(SpecialFieldText.FLAG_HELP, listOf("<b>flag3</b>", "<b>3</b>", "0", "7"))), calls(SpecialFields.Flag))
        assertEquals(
            listOf(TextCall(SpecialFieldText.FLAG_HELP, listOf("flagN", "<b>N</b>", "0", "7"))),
            calls(SpecialFields.Flag, Metadata(flag = null)),
        )
    }

    @Test
    fun `front side description has no example and unknown fields produce no text`() {
        assertEquals(listOf(TextCall(SpecialFieldText.FRONT_SIDE_HELP, emptyList())), calls(SpecialFields.FrontSide))
        assertEquals(emptyList(), calls(SpecialField("unknown")))
    }

    @Test
    fun `FrontSide is only offered on the answer and field ordering stays stable`() {
        val answer = SpecialFields.all(onAnswerSide = true)
        assertTrue(SpecialFields.FrontSide in answer)
        assertEquals(listOf("FrontSide", "Deck", "Subdeck", "Tags", "CardFlag", "Type", "Card", "CardID"), answer.map { it.name })
        assertFalse(SpecialFields.FrontSide in SpecialFields.all(onAnswerSide = false))
        assertEquals(answer.drop(1), SpecialFields.all(onAnswerSide = false))
    }
}
