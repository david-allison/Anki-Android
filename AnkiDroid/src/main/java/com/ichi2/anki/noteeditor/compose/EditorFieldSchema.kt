// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import com.ichi2.anki.libanki.Field
import com.ichi2.anki.libanki.NotetypeJson
import org.json.JSONObject

/** Text from deleted fields stays in the draft until explicitly discarded. */
data class EditorRemovedField(
    val name: String,
    val html: String,
)

internal fun Field.editorKey(): String = jsonObject.opt("id")?.takeUnless { it == JSONObject.NULL }?.let { "id:$it" } ?: "name:$name"

internal data class EditorFieldMapping(
    val indices: List<Int>,
    val html: List<String>,
    val removed: List<EditorRemovedField>,
)

internal fun mapEditorFields(
    oldKeys: List<String>,
    oldNames: List<String>,
    html: List<String>,
    type: NotetypeJson,
): EditorFieldMapping {
    require(oldKeys.size == html.size && oldNames.size == html.size && oldKeys.distinct().size == oldKeys.size)
    val indices = type.fields.map { oldKeys.indexOf(it.editorKey()) }
    return EditorFieldMapping(
        indices,
        indices.map { html.getOrElse(it) { "" } },
        html.indices.filter { it !in indices && html[it].isNotEmpty() }.map { EditorRemovedField(oldNames[it], html[it]) },
    )
}
