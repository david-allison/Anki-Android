// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.content.SharedPreferences
import android.os.Parcelable
import com.ichi2.anki.noteeditor.CustomToolbarButton
import kotlinx.parcelize.Parcelize
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

@Parcelize
data class EditorCustomToolbarButton(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val prefix: String,
    val suffix: String,
) : Parcelable

@Parcelize
data class EditorFieldToolbar(
    val hiddenButtons: Set<String> = emptySet(),
    val customButtons: List<EditorCustomToolbarButton> = emptyList(),
) : Parcelable

/** Device-local overrides for a field on all notes using the same note type. */
internal class EditorToolbarPreferences(
    private val preferences: SharedPreferences,
) {
    // TODO: Move field toolbar configuration into the Anki collection so it syncs with the note type.
    // Keep collection, note type and stable field identity separate from the temporary storage format.
    fun load(
        collection: String,
        noteType: Long,
        field: String,
    ): EditorFieldToolbar {
        val defaults = {
            EditorFieldToolbar(
                customButtons =
                    CustomToolbarButton
                        .fromStringSet(preferences.getStringSet("note_editor_custom_buttons", emptySet()).orEmpty())
                        .map {
                            EditorCustomToolbarButton(
                                "legacy:${it.index}",
                                it.buttonText.ifEmpty { (it.index + 1).toString() },
                                it.prefix,
                                it.suffix,
                            )
                        },
            )
        }
        val saved = preferences.getString(key(collection, noteType, field), null) ?: return defaults()
        return runCatching {
            val json = JSONObject(saved)
            val hidden = json.getJSONArray("hidden")
            val custom = json.getJSONArray("custom")
            EditorFieldToolbar(
                hiddenButtons = (0 until hidden.length()).map { hidden.getString(it) }.toSet(),
                customButtons =
                    (0 until custom.length()).map {
                        val button = custom.getJSONObject(it)
                        EditorCustomToolbarButton(
                            button.getString("id"),
                            button.getString("label"),
                            button.getString("prefix"),
                            button.getString("suffix"),
                        )
                    },
            )
        }.getOrElse { defaults() }
    }

    /** Call off the UI thread; a failed write must not appear saved in the editor. */
    fun save(
        collection: String,
        noteType: Long,
        field: String,
        toolbar: EditorFieldToolbar,
    ) {
        val json =
            JSONObject()
                .put("hidden", JSONArray(toolbar.hiddenButtons.toList()))
                .put(
                    "custom",
                    JSONArray(
                        toolbar.customButtons.map {
                            JSONObject()
                                .put("id", it.id)
                                .put("label", it.label)
                                .put("prefix", it.prefix)
                                .put("suffix", it.suffix)
                        },
                    ),
                )
        check(preferences.edit().putString(key(collection, noteType, field), json.toString()).commit()) {
            "Unable to save field toolbar settings."
        }
    }

    private fun key(
        collection: String,
        noteType: Long,
        field: String,
    ): String = "composeEditorFieldToolbar:" + JSONArray(listOf(collection, noteType, field)).toString()
}
