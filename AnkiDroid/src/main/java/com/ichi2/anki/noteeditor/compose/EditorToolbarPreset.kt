// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

/** Shared button choices; field quick actions and device layout are deliberately independent. */
@Parcelize
data class EditorToolbarPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val hiddenButtons: Set<String> = emptySet(),
    val customButtonIds: List<String> = emptyList(),
    val buttonOrder: List<String> = emptyList(),
) : Parcelable {
    fun applyTo(
        toolbar: EditorFieldToolbar,
        templates: List<EditorCustomToolbarButton>,
    ): EditorFieldToolbar {
        val byId = templates.associateBy { it.id }
        return toolbar.copy(
            hiddenButtons = hiddenButtons,
            customButtons = customButtonIds.mapNotNull(byId::get),
            buttonOrder = buttonOrder,
        )
    }

    fun withToolbar(toolbar: EditorFieldToolbar): EditorToolbarPreset =
        copy(
            hiddenButtons = toolbar.hiddenButtons,
            customButtonIds = toolbar.customButtons.map { it.id },
            buttonOrder = toolbar.buttonOrder,
        )

    companion object {
        const val DEFAULT_ID = "default"
    }
}
