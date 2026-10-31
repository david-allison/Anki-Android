// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import anki.search.SearchNode
import anki.search.searchNode
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = FlagSerializer::class)
enum class Flag(
    val code: Int,
) {
    NONE(0),
    RED(1),
    ORANGE(2),
    GREEN(3),
    BLUE(4),
    PINK(5),
    TURQUOISE(6),
    PURPLE(7),
    ;

    /**
     * Creates a [SearchNode.Flag] for use in a [SearchNode].
     *
     * Prefer [toSearchNode] for simple [SearchNode] creation.
     *
     * ```kotlin
     * val red = Flag.RED
     * val node = searchNode { flag = red.toSearchValue() }
     * ```
     */
    fun toSearchValue(): SearchNode.Flag =
        when (this) {
            // The protobuf value does NOT correspond to the value in the DB
            // SearchNode.Flag.FLAG_RED == 2; Flag.RED.code == 1
            NONE -> SearchNode.Flag.FLAG_NONE
            RED -> SearchNode.Flag.FLAG_RED
            ORANGE -> SearchNode.Flag.FLAG_ORANGE
            GREEN -> SearchNode.Flag.FLAG_GREEN
            BLUE -> SearchNode.Flag.FLAG_BLUE
            PINK -> SearchNode.Flag.FLAG_PINK
            TURQUOISE -> SearchNode.Flag.FLAG_TURQUOISE
            PURPLE -> SearchNode.Flag.FLAG_PURPLE
        }

    /**
     * Creates a [SearchNode] for building a search string.
     *
     * Use [toSearchValue] when generating a complex `SearchNode`
     *
     * ```kotlin
     * val searchNode = Flag.RED.toSearchNode()
     * val searchString = col.buildSearchString(listOf(searchNode))
     * ```
     */
    fun toSearchNode(): SearchNode = searchNode { flag = toSearchValue() }

    companion object {
        val MAX_CODE: Int = entries.maxOf { it.code }
        val MIN_CODE: Int = entries.minOf { it.code }

        fun fromCode(code: Int) = Flag.entries.first { it.code == code }
    }
}

/** Serializer for [Flag] */
object FlagSerializer : KSerializer<Flag> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Flag", PrimitiveKind.INT)

    override fun serialize(
        encoder: Encoder,
        value: Flag,
    ) {
        encoder.encodeInt(value.code)
    }

    override fun deserialize(decoder: Decoder): Flag {
        val code = decoder.decodeInt()
        return Flag.fromCode(code)
    }
}
