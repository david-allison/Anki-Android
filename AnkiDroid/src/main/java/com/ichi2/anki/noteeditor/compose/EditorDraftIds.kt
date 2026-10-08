// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import com.ichi2.anki.NoteEditorFragment
import com.ichi2.anki.NoteEditorFragment.Companion.NoteEditorCaller
import org.json.JSONArray
import java.security.MessageDigest
import java.util.UUID

/**
 * Native discovery pointers contain only draft IDs. Fields and recovery metadata remain together
 * in IndexedDB. A second active editor receives its own ID instead of sharing a live web draft.
 */
internal class EditorDraftIds(
    private val preferences: SharedPreferences,
    private val activeIds: MutableSet<String> = claimedIds,
) {
    private var key: String? = null
    private var id: String? = null

    fun acquire(
        collection: String,
        target: String,
        savedId: String?,
    ): String =
        synchronized(activeIds) {
            check(id == null) { "This editor already has a draft ID." }
            val preferenceKey = "composeEditorDraft:" + draftKeyHash("$collection\u0000$target")
            val candidate = savedId ?: preferences.getString(preferenceKey, null)
            val selected = candidate?.takeUnless { it in activeIds } ?: UUID.randomUUID().toString()
            activeIds.add(selected)
            key = preferenceKey
            id = selected
            selected
        }

    /** Called after an IndexedDB checkpoint commits. The small pointer write must also complete. */
    fun remember() =
        synchronized(activeIds) {
            val key = checkNotNull(key)
            val id = checkNotNull(id)
            val previous = preferences.getString(key, null)
            if (previous == id || previous in activeIds) return@synchronized
            check(preferences.edit().putString(key, id).commit()) { "Could not record the note editor draft." }
        }

    /** An old editor must never remove a discovery pointer subsequently owned by another editor. */
    fun forget() =
        synchronized(activeIds) {
            val key = checkNotNull(key)
            if (preferences.getString(key, null) == id) {
                check(preferences.edit().remove(key).commit()) { "Could not clear the note editor draft." }
            }
        }

    fun release() =
        synchronized(activeIds) {
            id?.let(activeIds::remove)
            id = null
        }

    companion object {
        private val claimedIds = mutableSetOf<String>()
    }
}

/** A new share/copy must not replace, or restore over, an unrelated ordinary Add draft. */
internal fun editorAddDraftTarget(arguments: Bundle): String {
    val caller = arguments.getInt(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.DECKPICKER.value)
    val initialText =
        listOf(
            NoteEditorFragment.EXTRA_CONTENTS,
            NoteEditorFragment.EXTRA_TEXT_FROM_SEARCH_VIEW,
            Intent.EXTRA_TEXT,
            Intent.EXTRA_SUBJECT,
            Intent.EXTRA_PROCESS_TEXT,
            NoteEditorFragment.SOURCE_TEXT,
            NoteEditorFragment.TARGET_TEXT,
            NoteEditorFragment.EXTRA_ID,
        ).map { arguments.getCharSequence(it)?.toString() }
    val tags = arguments.getStringArray(NoteEditorFragment.EXTRA_TAGS)?.toList().orEmpty()
    val seededCaller =
        caller in
            setOf(
                NoteEditorCaller.NOTEEDITOR.value,
                NoteEditorCaller.NOTEEDITOR_INTENT_ADD.value,
                NoteEditorCaller.INSTANT_NOTE_EDITOR.value,
            )
    if (!seededCaller && initialText.all { it.isNullOrEmpty() } && tags.isEmpty()) return "add"
    val payload =
        JSONArray()
            .put(caller)
            .put(arguments.getLong(NoteEditorFragment.EXTRA_DID))
            .put(JSONArray(initialText))
            .put(JSONArray(tags))
    return "add-seeded:" + draftKeyHash(payload.toString())
}

private fun draftKeyHash(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
