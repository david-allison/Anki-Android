// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.jsaddons

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AddonCollectionApiTest : RobolectricTest() {
    private fun grant(
        addon: String,
        permission: AddonPermission,
    ) = AddonStateStore(targetContext).grant(addon, permission)

    /** Runs a method and returns the parsed `{ok, value|error}` envelope */
    private suspend fun call(
        addon: String,
        method: String,
        args: String = "{}",
    ): JsonObject = Json.parseToJsonElement(AddonCollectionApi.handle(targetContext, addon, method, args)) as JsonObject

    private val decksRead = AddonPermission.Scoped(AddonPermission.Scoped.Entity.DECKS, AddonPermission.Scoped.Access.READ)

    @Test
    fun ungrantedCallIsRejectedTest() =
        runTest {
            // no permission granted
            val envelope = call("addon", "decks.all")
            assertFalse(envelope["ok"]!!.jsonPrimitive.booleanOrNull!!, "the call is refused")
            assertTrue(envelope["error"]!!.jsonPrimitive.content.contains("decks:read"), "the error names the missing permission")
        }

    @Test
    fun unknownMethodIsRejectedTest() =
        runTest {
            grant("addon", decksRead)
            val envelope = call("addon", "decks.teleport")
            assertFalse(envelope["ok"]!!.jsonPrimitive.booleanOrNull!!)
        }

    @Test
    fun decksReadListsDecksWhenGrantedTest() =
        runTest {
            grant("addon", decksRead)
            col.decks.addNormalDeckWithName("Addon Test Deck")

            val value = call("addon", "decks.all")["value"] as JsonArray
            val names = value.map { (it as JsonObject)["name"]!!.jsonPrimitive.content }
            assertTrue(names.contains("Addon Test Deck"), "the created deck is listed")
        }

    @Test
    fun decksCurrentReturnsSelectedDeckTest() =
        runTest {
            grant("addon", decksRead)

            val current = call("addon", "decks.current")["value"]!!.jsonObject
            assertTrue(current["name"]!!.jsonPrimitive.content.isNotEmpty(), "the current deck has a name")
        }

    @Test
    fun decksWriteCreatesADeckWhenGrantedTest() =
        runTest {
            val decksWrite = AddonPermission.Scoped(AddonPermission.Scoped.Entity.DECKS, AddonPermission.Scoped.Access.WRITE)
            // read alone must not allow a write
            grant("addon", decksRead)
            assertFalse(
                call("addon", "decks.add", """{"name":"Nope"}""")["ok"]!!.jsonPrimitive.booleanOrNull!!,
                "read does not grant write",
            )

            grant("addon", decksWrite)
            val id = call("addon", "decks.add", """{"name":"Created By Addon"}""")["value"]!!.jsonObject["id"]!!.jsonPrimitive.long
            assertTrue(id > 0, "a deck id is returned")
            assertEquals("Created By Addon", col.decks.name(id))
        }

    @Test
    fun notesReadFindsAndReadsNotesTest() =
        runTest {
            grant("addon", AddonPermission.Scoped(AddonPermission.Scoped.Entity.NOTES, AddonPermission.Scoped.Access.READ))
            val note = addBasicNote("Front text", "Back text")

            val found = call("addon", "notes.find", """{"query":"Front text"}""")["value"] as JsonArray
            assertTrue(found.map { it.jsonPrimitive.long }.contains(note.id), "the note is found")

            val info = call("addon", "notes.info", """{"noteId":"${note.id}"}""")["value"]!!.jsonObject
            val fields = (info["fields"] as JsonArray).map { it.jsonPrimitive.content }
            assertTrue(fields.contains("Front text"), "the note's fields are readable")
        }

    @Test
    fun cardsReadFindsAndReadsCardsTest() =
        runTest {
            grant("addon", AddonPermission.Scoped(AddonPermission.Scoped.Entity.CARDS, AddonPermission.Scoped.Access.READ))
            val note = addBasicNote("Q", "A")
            val cardId = note.cardIds(col).first()

            val found = call("addon", "cards.find", """{"query":"Q"}""")["value"] as JsonArray
            assertTrue(found.map { it.jsonPrimitive.long }.contains(cardId), "the card is found")

            val info = call("addon", "cards.info", """{"cardId":"$cardId"}""")["value"]!!.jsonObject
            assertEquals(note.id, info["noteId"]!!.jsonPrimitive.long)
        }

    @Test
    fun notesWriteAddsAndRemovesTagsTest() =
        runTest {
            grant("addon", AddonPermission.Scoped(AddonPermission.Scoped.Entity.NOTES, AddonPermission.Scoped.Access.WRITE))
            val note = addBasicNote("F", "B")

            call("addon", "notes.addTags", """{"noteIds":[${note.id}],"tags":["addon-tag"]}""")
            assertTrue(col.getNote(note.id).tags.contains("addon-tag"), "the tag was added")

            call("addon", "notes.removeTags", """{"noteIds":[${note.id}],"tags":["addon-tag"]}""")
            assertFalse(col.getNote(note.id).tags.contains("addon-tag"), "the tag was removed")
        }
}
