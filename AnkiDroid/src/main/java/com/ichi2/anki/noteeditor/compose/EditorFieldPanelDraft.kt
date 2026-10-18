// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** Only fields edited in the panel are retained; shared presets have one pending copy. */
@Parcelize
data class EditorFieldPanelDraft(
    val target: EditorFieldTarget,
    val initialPresets: List<EditorToolbarPreset>,
    val presets: List<EditorToolbarPreset> = initialPresets,
    val originalFields: Map<Int, EditorPanelFieldSettings> = emptyMap(),
    val fields: Map<Int, EditorPanelFieldSettings> = emptyMap(),
) : Parcelable {
    val hasChanges: Boolean get() = presets != initialPresets || fields.any { (index, value) -> value != originalFields[index] }

    fun settings(
        index: Int,
        field: EditorFieldState,
    ): EditorPanelFieldSettings =
        (fields[index] ?: EditorPanelFieldSettings(field.toolbarPresetId, field.toolbar.showToolbar))
            .let { if (presets.any { preset -> preset.id == it.presetId }) it else it.copy(presetId = EditorToolbarPreset.DEFAULT_ID) }

    fun toolbar(
        index: Int,
        field: EditorFieldState,
    ): EditorToolbarConfiguration = settings(index, field).let { EditorToolbarConfiguration(presets, it.presetId, it.showToolbar) }

    fun withToolbar(
        index: Int,
        field: EditorFieldState,
        configuration: EditorToolbarConfiguration,
    ): EditorFieldPanelDraft =
        withField(index, field, settings(index, field).copy(presetId = configuration.presetId, showToolbar = configuration.showToolbar))
            .copy(presets = configuration.presets)

    private fun withField(
        index: Int,
        field: EditorFieldState,
        settings: EditorPanelFieldSettings,
    ): EditorFieldPanelDraft =
        copy(
            originalFields = if (index in originalFields) originalFields else originalFields + (index to settings(index, field)),
            fields = fields + (index to settings),
        )

    /** Deleted presets fall back to Default, including assignments made on other pages. */
    fun changedFields(): Map<Int, EditorPanelFieldSettings> =
        fields
            .filter { (index, value) -> value != originalFields[index] }
            .mapValues { (_, value) ->
                if (presets.any { it.id == value.presetId }) value else value.copy(presetId = EditorToolbarPreset.DEFAULT_ID)
            }
}

@Parcelize
data class EditorPanelFieldSettings(
    val presetId: String,
    val showToolbar: Boolean,
) : Parcelable
