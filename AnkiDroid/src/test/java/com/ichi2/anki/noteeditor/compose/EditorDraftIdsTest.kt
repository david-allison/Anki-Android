// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class EditorDraftIdsTest {
    private val preferences = SPMockBuilder().createSharedPreferences()
    private val active = mutableSetOf<String>()

    @Test
    fun `a checkpoint makes the draft discoverable after a fresh process launch`() {
        val first = editor()
        val draft = first.acquire("collection A", "add", null)
        assertTrue(preferences.all.isEmpty())
        first.remember()
        assertEquals(listOf(draft), preferences.all.values.toList())

        val afterProcessDeath = EditorDraftIds(preferences, mutableSetOf())
        assertEquals(draft, afterProcessDeath.acquire("collection A", "add", null))
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

    private fun editor() = EditorDraftIds(preferences, active)
}
