// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>

package com.ichi2.anki.libanki

/** Deck editing and name validation, independent of dialogs and feedback presentation. */
class DeckEditor(
    private val decks: Decks,
) {
    enum class NameValidation { INVALID, ALREADY_EXISTS, VALID }

    fun validateName(
        input: String,
        parentId: DeckId? = null,
        renamedDeckId: DeckId? = null,
    ): NameValidation {
        val name = if (parentId == null) input else decks.getSubdeckName(parentId, input)
        if (name == null || !Decks.isValidDeckName(name)) return NameValidation.INVALID
        val existingDeckId = decks.idForName(name)
        return if (existingDeckId != null && existingDeckId != renamedDeckId) {
            NameValidation.ALREADY_EXISTS
        } else {
            NameValidation.VALID
        }
    }

    /** Returns the created deck, or null for invalid input. Backend failures propagate to the caller. */
    fun create(name: String): DeckId? = if (Decks.isValidDeckName(name)) decks.id(name) else null

    fun createSubdeck(
        parentId: DeckId,
        name: String?,
    ): DeckId? = decks.getSubdeckName(parentId, name)?.let(::create)

    /** Returns whether a valid rename changed the deck. Backend failures propagate to the caller. */
    fun rename(
        deckId: DeckId,
        name: String,
    ): Boolean = Decks.isValidDeckName(name) && decks.rename(deckId, name).deck
}
