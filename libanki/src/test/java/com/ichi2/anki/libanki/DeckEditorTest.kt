// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>

package com.ichi2.anki.libanki

import com.ichi2.anki.libanki.testutils.InMemoryAnkiTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DeckEditorTest : InMemoryAnkiTest() {
    private val editor get() = DeckEditor(col.decks)

    @Test
    fun `creates a fully qualified subdeck`() {
        val parent = col.decks.id("Deck Name")
        val created = assertNotNull(editor.createSubdeck(parent, "filteredDeck"))

        assertEquals(col.decks.id(col.decks.getSubdeckName(parent, "filteredDeck")!!), created)
    }

    @Test
    fun `creates a deck`() {
        val created = editor.create("Deck Name")

        assertEquals(col.decks.byName("Deck Name")!!.id, created)
    }

    @Test
    fun `creation preserves quotes`() {
        val name = "New \"Quoted\" Deck"
        val created = assertNotNull(editor.create(name))

        assertEquals(name, col.decks.name(created))
    }

    @Test
    fun `renames an existing deck`() {
        val id = col.decks.id("Deck Name")

        assertTrue(editor.rename(id, "New Deck Name"))
        assertEquals("New Deck Name", col.decks.name(id))
    }

    @Test
    fun `renaming preserves quotes`() {
        val id = col.decks.id("Deck Name")
        val name = "New \"Quoted\" Deck Name"

        assertTrue(editor.rename(id, name))
        assertEquals(name, col.decks.name(id))
    }

    @Test
    fun `a deck can keep its own name`() {
        val id = col.decks.id("Deck Name")

        assertEquals(DeckEditor.NameValidation.VALID, editor.validateName("Deck Name", renamedDeckId = id))
    }

    @Test
    fun `an extra colon does not make a rename conflict with itself`() {
        val id = col.decks.id("a::b")

        assertEquals(DeckEditor.NameValidation.VALID, editor.validateName("a:::b", renamedDeckId = id))
    }
}
