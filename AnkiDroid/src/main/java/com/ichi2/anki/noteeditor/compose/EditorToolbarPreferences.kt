// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Parcelable
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
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
    val quickActions: List<String> = EditorQuickAction.defaults,
    val showToolbar: Boolean = true,
) : Parcelable

/** Device-local overrides for a field on all notes using the same note type. */
internal class EditorToolbarPreferences(
    private val preferences: SharedPreferences,
) {
    // TODO: Move presets, field toolbar/quick-action configuration and custom templates into the Anki collection for sync.
    // Keep collection, note type and stable field identity separate from the temporary storage format.
    fun load(
        collection: String,
        noteType: Long,
        field: String,
        presets: List<EditorToolbarPreset> = loadPresets(),
    ): EditorFieldToolbar {
        val library = library()
        val buttons = library.getJSONArray("buttons").buttons().associateBy { it.id }
        val defaults = EditorFieldToolbar(customButtons = library.getJSONArray("defaults").strings().mapNotNull(buttons::get))
        val json = fieldSettings(collection, noteType, field)
        val local =
            runCatching {
                EditorFieldToolbar(
                    showToolbar = json.optBoolean("showToolbar", true),
                    hiddenButtons =
                        json
                            .optJSONArray("hidden")
                            ?.strings()
                            ?.toSet()
                            .orEmpty(),
                    customButtons = json.optJSONArray("customIds")?.strings()?.mapNotNull(buttons::get) ?: defaults.customButtons,
                    buttonOrder = json.optJSONArray("order")?.strings().orEmpty(),
                    quickActions =
                        json
                            .optJSONArray("quickActions")
                            ?.strings()
                            ?.distinct() ?: defaults.quickActions,
                )
            }.getOrElse { defaults }
        val preset = presets.first { it.id == presetId(json, presets) }
        return preset.applyTo(local, buttons.values.toList())
    }

    fun loadPresets(): List<EditorToolbarPreset> {
        val saved = preferences.getString(PRESETS_KEY, null)
        val presets =
            if (saved == null) {
                listOf(
                    EditorToolbarPreset(
                        id = EditorToolbarPreset.DEFAULT_ID,
                        name = "",
                        customButtonIds = library().getJSONArray("defaults").strings(),
                    ),
                )
            } else {
                val array = JSONArray(saved)
                (0 until array.length()).map { index ->
                    val preset = array.getJSONObject(index)
                    EditorToolbarPreset(
                        id = preset.getString("id"),
                        name = preset.getString("name"),
                        hiddenButtons = preset.getJSONArray("hidden").strings().toSet(),
                        customButtonIds = preset.getJSONArray("customIds").strings(),
                        buttonOrder = preset.getJSONArray("order").strings(),
                    )
                }
            }
        return migrateFieldToolbars(presets)
    }

    /** Preserve old private toolbars as shared presets, once; never scan all fields on subsequent loads. */
    private fun migrateFieldToolbars(existing: List<EditorToolbarPreset>): List<EditorToolbarPreset> {
        if (preferences.getBoolean(SHARED_TOOLBARS_KEY, false)) return existing
        // Import copied custom definitions before reading their new IDs from field settings.
        val defaultButtons = library().getJSONArray("defaults").strings()
        val presets = existing.toMutableList()
        val editor = preferences.edit()
        var nextName = 1
        preferences.all.filterKeys { it.startsWith(FIELD_PREFIX) }.forEach { (key, value) ->
            val json = runCatching { JSONObject(value as String) }.getOrNull() ?: return@forEach
            if (json.has("preset") || !json.has("hidden")) return@forEach
            val hidden =
                json
                    .optJSONArray("hidden")
                    ?.strings()
                    ?.toSet()
                    .orEmpty()
            val custom = json.optJSONArray("customIds")?.strings() ?: defaultButtons
            val order = json.optJSONArray("order")?.strings().orEmpty()
            val preset =
                presets.firstOrNull {
                    it.hiddenButtons == hidden && it.customButtonIds == custom && it.buttonOrder == order
                } ?: run {
                    var name: String
                    do {
                        name = AnkiDroidApp.appResources.getString(R.string.compose_editor_imported_toolbar, nextName++)
                    } while (presets.any { it.name.equals(name, ignoreCase = true) })
                    EditorToolbarPreset(name = name, hiddenButtons = hidden, customButtonIds = custom, buttonOrder = order)
                        .also(presets::add)
                }
            editor.putString(key, json.put("preset", preset.id).toString())
        }
        check(editor.putString(PRESETS_KEY, presets.presetJson().toString()).putBoolean(SHARED_TOOLBARS_KEY, true).commit()) {
            "Unable to migrate field toolbar presets."
        }
        return presets
    }

    /** Replacing the library atomically also makes deleted assignments inherit Default. */
    fun savePresets(presets: List<EditorToolbarPreset>) {
        validatePresets(presets)
        check(preferences.edit().putString(PRESETS_KEY, presets.presetJson().toString()).commit()) { "Unable to save toolbar presets." }
    }

    /** Save the library and field binding together, including a newly created preset. */
    fun saveConfiguration(
        collection: String,
        noteType: Long,
        field: String,
        configuration: EditorToolbarConfiguration,
    ) {
        validatePresets(configuration.presets)
        require(configuration.presets.any { it.id == configuration.presetId })
        val json =
            fieldSettings(collection, noteType, field)
                .put("preset", configuration.presetId)
                .put("showToolbar", configuration.showToolbar)
        check(
            preferences
                .edit()
                .putString(PRESETS_KEY, configuration.presets.presetJson().toString())
                .putString(key(collection, noteType, field), json.toString())
                .commit(),
        ) { "Unable to save toolbar configuration." }
    }

    private fun validatePresets(presets: List<EditorToolbarPreset>) {
        require(presets.count { it.id == EditorToolbarPreset.DEFAULT_ID } == 1)
        require(presets.map { it.id }.distinct().size == presets.size)
        require(presets.all { it.id.isNotBlank() && (it.id == EditorToolbarPreset.DEFAULT_ID || it.name.isNotBlank()) })
        val named = presets.filter { it.id != EditorToolbarPreset.DEFAULT_ID }.map { it.name.trim().lowercase(java.util.Locale.ROOT) }
        require(named.distinct().size == named.size)
    }

    /** Commit all visited fields and the shared library in a single preferences transaction. */
    @SuppressLint("UseKtx") // The KTX helper discards commit()'s result; Save must report storage failures.
    fun savePanel(
        collection: String,
        noteType: Long,
        presets: List<EditorToolbarPreset>,
        fields: Map<String, EditorPanelFieldSettings>,
    ) {
        validatePresets(presets)
        require(fields.values.all { field -> presets.any { it.id == field.presetId } })
        val edit = preferences.edit().putString(PRESETS_KEY, presets.presetJson().toString())
        fields.forEach { (field, settings) ->
            val json =
                fieldSettings(collection, noteType, field)
                    .put("preset", settings.presetId)
                    .put("showToolbar", settings.showToolbar)
            edit.putString(key(collection, noteType, field), json.toString())
        }
        check(edit.commit()) { "Unable to save field configuration." }
    }

    private fun List<EditorToolbarPreset>.presetJson(): JSONArray =
        JSONArray(
            map {
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name.trim())
                    .put("hidden", JSONArray(it.hiddenButtons.toList()))
                    .put("customIds", JSONArray(it.customButtonIds))
                    .put("order", JSONArray(it.buttonOrder))
            },
        )

    fun presetId(
        collection: String,
        noteType: Long,
        field: String,
        presets: List<EditorToolbarPreset> = loadPresets(),
    ): String = presetId(fieldSettings(collection, noteType, field), presets)

    private fun presetId(
        json: JSONObject,
        presets: List<EditorToolbarPreset>,
    ): String = json.optString("preset").takeIf { id -> presets.any { it.id == id } } ?: EditorToolbarPreset.DEFAULT_ID

    fun selectPreset(
        collection: String,
        noteType: Long,
        field: String,
        presetId: String,
    ) {
        require(loadPresets().any { it.id == presetId })
        val json = fieldSettings(collection, noteType, field).put("preset", presetId)
        check(
            preferences.edit().putString(key(collection, noteType, field), json.toString()).commit(),
        ) { "Unable to assign toolbar preset." }
    }

    fun saveToolbarVisibility(
        collection: String,
        noteType: Long,
        field: String,
        visible: Boolean,
    ) {
        val json = fieldSettings(collection, noteType, field).put("showToolbar", visible)
        check(
            preferences.edit().putString(key(collection, noteType, field), json.toString()).commit(),
        ) { "Unable to save toolbar visibility." }
    }

    fun saveQuickActions(
        collection: String,
        noteType: Long,
        field: String,
        actions: List<String>,
    ) {
        val json = fieldSettings(collection, noteType, field).put("quickActions", JSONArray(actions.distinct()))
        check(preferences.edit().putString(key(collection, noteType, field), json.toString()).commit()) { "Unable to save quick actions." }
    }

    private fun fieldSettings(
        collection: String,
        noteType: Long,
        field: String,
    ): JSONObject =
        runCatching { JSONObject(preferences.getString(key(collection, noteType, field), null) ?: "{}") }.getOrElse { JSONObject() }

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
        const val SHARED_TOOLBARS_KEY = "composeEditorSharedToolbarsMigrated"
        const val PRESETS_KEY = "composeEditorToolbarPresets"
    }
}
