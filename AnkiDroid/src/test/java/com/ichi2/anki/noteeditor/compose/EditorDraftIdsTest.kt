// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import org.junit.Test
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class EditorDraftIdsTest {
    private val preferences = SPMockBuilder().createSharedPreferences()
    private val active = mutableSetOf<String>()

    @Test
    fun `fresh ID skips lookup once even if attachment never records its checkpoint`() {
        val draft = editor()
        draft.acquire("collection", "add", null)
        assertFalse(draft.takeRecoveryRequired())

        // JavaScript may commit after native cancellation, before remember or attachment completes.
        assertTrue(preferences.all.isEmpty())
        assertTrue(draft.takeRecoveryRequired())
    }

    @Test
    fun `saved instance ID requires recovery even without a discovery pointer`() {
        val draft = editor()
        assertEquals("saved-instance", draft.acquire("collection", "add", "saved-instance"))
        assertTrue(preferences.all.isEmpty())
        assertTrue(draft.takeRecoveryRequired())
    }

    @Test
    fun `a checkpoint makes the draft discoverable after a fresh process launch`() {
        val first = editor()
        val draft = first.acquire("collection A", "add", null)
        assertTrue(preferences.all.isEmpty())
        first.remember()
        assertEquals(listOf(draft), preferences.all.values.toList())

        val afterProcessDeath = EditorDraftIds(preferences, mutableSetOf())
        assertEquals(draft, afterProcessDeath.acquire("collection A", "add", null))
        assertTrue(afterProcessDeath.takeRecoveryRequired())
    }

    @Test
    fun `collection and editor target each isolate discovery`() {
        val first = editor()
        val draft = first.acquire("collection A", "edit:1:2:2", null)
        first.remember()
        first.release()
        assertNotEquals(draft, editor().acquire("collection B", "edit:1:2:2", null))
        assertNotEquals(draft, editor().acquire("collection A", "edit:3:4:4", null))
        assertNotEquals(draft, editor().acquire("collection A", "add", null))
    }

    @Test
    fun `saved instance ID takes precedence over the discovery pointer`() {
        val first = editor()
        first.acquire("collection", "add", null)
        first.remember()
        first.release()
        assertEquals("saved-instance", editor().acquire("collection", "add", "saved-instance"))
    }

    @Test
    fun `two active editors do not share a draft or steal its pointer`() {
        val first = editor()
        val firstId = first.acquire("collection", "add", null)
        first.remember()
        val second = editor()
        val secondId = second.acquire("collection", "add", null)
        assertFalse(second.takeRecoveryRequired())
        assertTrue(second.takeRecoveryRequired())
        second.remember()
        assertNotEquals(firstId, secondId)
        assertEquals(listOf(firstId), preferences.all.values.toList())
        second.forget()
        assertEquals(listOf(firstId), preferences.all.values.toList())
    }

    @Test
    fun `forget compares IDs so an old owner cannot delete a newer pointer`() {
        val first = editor()
        first.acquire("collection", "add", null)
        first.remember()
        val afterProcessDeath = EditorDraftIds(preferences, mutableSetOf())
        val newerId = afterProcessDeath.acquire("collection", "add", "newer-session")
        afterProcessDeath.remember()
        first.forget()
        assertEquals(listOf(newerId), preferences.all.values.toList())
        afterProcessDeath.forget()
        assertTrue(preferences.all.isEmpty())
    }

    @Test
    fun `released drafts can be claimed by a replacement editor`() {
        val first = editor()
        val id = first.acquire("collection", "add", null)
        first.remember()
        first.release()
        assertEquals(id, editor().acquire("collection", "add", null))
    }

    @Test
    fun `drafts stored with legacy hex formatting remain discoverable for every digest byte`() {
        val digestBytes = mutableSetOf<Int>()
        repeat(256) { index ->
            val collection = "collection $index"
            val target = "edit:$index"
            val digest = MessageDigest.getInstance("SHA-256").digest("$collection\u0000$target".toByteArray())
            digestBytes.addAll(digest.map { it.toInt() and 0xff })
            val legacyKey = "composeEditorDraft:" + digest.joinToString("") { "%02x".format(it) }
            val existingId = "existing-draft-$index"
            preferences.edit().putString(legacyKey, existingId).commit()

            val draft = editor()
            assertEquals(existingId, draft.acquire(collection, target, null))
            assertTrue(draft.takeRecoveryRequired())
            draft.release()
        }
        assertEquals((0..255).toSet(), digestBytes)
    }

    @Test
    fun `non ASCII collection retains its existing UTF8 SHA256 draft key`() {
        val key = "composeEditorDraft:2eaace35c63a47540dc76732d8bae8b71f4ea47167dafacf17d299c274b15c75"
        preferences.edit().putString(key, "existing-unicode-draft").commit()

        val draft = editor()
        assertEquals("existing-unicode-draft", draft.acquire("/collections/日本語/café/🗃️", "edit:12:34:34", null))
        assertTrue(draft.takeRecoveryRequired())
    }

    private fun editor() = EditorDraftIds(preferences, active)
}
