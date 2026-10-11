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
    val buttonOrder: List<String> = emptyList(),
) : Parcelable

/** Device-local overrides for a field on all notes using the same note type. */
internal class EditorToolbarPreferences(
    private val preferences: SharedPreferences,
) {
    // TODO: Move field toolbar configuration and custom templates into the Anki collection for sync.
    // Keep collection, note type and stable field identity separate from the temporary storage format.
    fun load(
        collection: String,
        noteType: Long,
        field: String,
    ): EditorFieldToolbar {
        val library = library()
        val buttons = library.getJSONArray("buttons").buttons().associateBy { it.id }
        val defaults = EditorFieldToolbar(customButtons = library.getJSONArray("defaults").strings().mapNotNull(buttons::get))
        val saved = preferences.getString(key(collection, noteType, field), null) ?: return defaults
        return runCatching {
            val json = JSONObject(saved)
            EditorFieldToolbar(
                hiddenButtons = json.getJSONArray("hidden").strings().toSet(),
                customButtons = json.getJSONArray("customIds").strings().mapNotNull(buttons::get),
                buttonOrder = json.optJSONArray("order")?.strings().orEmpty(),
            )
        }.getOrElse { defaults }
    }

    fun loadCustomButtons(): List<EditorCustomToolbarButton> = library().getJSONArray("buttons").buttons()

    /** Call off the UI thread; a failed write must not appear saved in the editor. */
    fun saveCustomButtons(buttons: List<EditorCustomToolbarButton>) {
        require(buttons.map { it.id }.distinct().size == buttons.size)
        val library = library()
        library.put("buttons", buttons.json())
        // Removing a template also removes it from the defaults inherited from the old editor.
        library.put("defaults", JSONArray(library.getJSONArray("defaults").strings().filter { id -> buttons.any { it.id == id } }))
        check(preferences.edit().putString(LIBRARY_KEY, library.toString()).commit()) {
            "Unable to save custom toolbar buttons."
        }
    }

    /** The definitions are resolved from the global library; field overrides only store references. */
    fun save(
        collection: String,
        noteType: Long,
        field: String,
        toolbar: EditorFieldToolbar,
    ) {
        val available = loadCustomButtons().map { it.id }.toSet()
        require(toolbar.customButtons.all { it.id in available })
        val json =
            JSONObject()
                .put("hidden", JSONArray(toolbar.hiddenButtons.toList()))
                .put("customIds", JSONArray(toolbar.customButtons.map { it.id }))
                .put("order", JSONArray(toolbar.buttonOrder))
        check(preferences.edit().putString(key(collection, noteType, field), json.toString()).commit()) {
            "Unable to save field toolbar settings."
        }
    }

    /** Import legacy defaults and copied field definitions together, once, without losing edited variants. */
    private fun library(): JSONObject {
        preferences.getString(LIBRARY_KEY, null)?.let { return JSONObject(it) }
        val buttons = mutableListOf<EditorCustomToolbarButton>()

        fun import(button: EditorCustomToolbarButton): String {
            buttons
                .firstOrNull { it.label == button.label && it.prefix == button.prefix && it.suffix == button.suffix }
                ?.let { return it.id }
            val imported = if (buttons.any { it.id == button.id }) button.copy(id = UUID.randomUUID().toString()) else button
            buttons.add(imported)
            return imported.id
        }
        val defaults =
            CustomToolbarButton.fromStringSet(preferences.getStringSet("note_editor_custom_buttons", emptySet()).orEmpty()).map {
                import(
                    EditorCustomToolbarButton(
                        "legacy:${it.index}",
                        it.buttonText.ifEmpty { (it.index + 1).toString() },
                        it.prefix,
                        it.suffix,
                    ),
                )
            }
        val editor = preferences.edit()
        preferences.all.filterKeys { it.startsWith(FIELD_PREFIX) }.forEach { (key, value) ->
            val json = runCatching { JSONObject(value as String) }.getOrNull() ?: return@forEach
            val copied = json.optJSONArray("custom") ?: return@forEach
            val definitions = runCatching { copied.buttons() }.getOrNull() ?: return@forEach
            json.put("customIds", JSONArray(definitions.map(::import)))
            json.remove("custom")
            editor.putString(key, json.toString())
        }
        val library = JSONObject().put("buttons", buttons.json()).put("defaults", JSONArray(defaults))
        check(editor.putString(LIBRARY_KEY, library.toString()).commit()) { "Unable to migrate toolbar settings." }
        return library
    }

    private fun key(
        collection: String,
        noteType: Long,
        field: String,
    ): String = FIELD_PREFIX + JSONArray(listOf(collection, noteType, field)).toString()

    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

    private fun JSONArray.buttons(): List<EditorCustomToolbarButton> =
        (0 until length()).map {
            val button = getJSONObject(it)
            EditorCustomToolbarButton(
                button.getString("id"),
                button.getString("label"),
                button.getString("prefix"),
                button.getString("suffix"),
            )
        }

    private fun List<EditorCustomToolbarButton>.json(): JSONArray =
        JSONArray(
            map {
                JSONObject()
                    .put("id", it.id)
                    .put("label", it.label)
                    .put("prefix", it.prefix)
                    .put("suffix", it.suffix)
            },
        )

    private companion object {
        const val FIELD_PREFIX = "composeEditorFieldToolbar:"
        const val LIBRARY_KEY = "composeEditorCustomButtonTemplates"
    }
}
