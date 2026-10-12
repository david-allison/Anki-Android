// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.web

import org.json.JSONObject

/** Display and typing preferences, independent of the note's saved HTML. */
data class WebEditorSettings(
    val shrinkImages: Boolean = true,
    val mathjaxPreview: Boolean = true,
    val autoCloseTags: Boolean = true,
    val fontSize: Int? = null,
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("shrinkImages", shrinkImages)
            .put("mathjaxPreview", mathjaxPreview)
            .put("autoCloseTags", autoCloseTags)
            .put("fontSize", fontSize ?: JSONObject.NULL)

    companion object {
        fun fromJson(value: JSONObject?) =
            WebEditorSettings(
                shrinkImages = value?.optBoolean("shrinkImages", true) ?: true,
                mathjaxPreview = value?.optBoolean("mathjaxPreview", true) ?: true,
                autoCloseTags = value?.optBoolean("autoCloseTags", true) ?: true,
                fontSize = value?.optInt("fontSize")?.takeIf { it in 1..99 },
            )
    }
}
