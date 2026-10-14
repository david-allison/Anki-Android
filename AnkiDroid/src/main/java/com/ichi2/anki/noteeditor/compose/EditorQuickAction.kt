// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.multimedia.MultimediaActionHandler
import com.ichi2.anki.noteeditor.web.WebEditorFieldAction
import com.ichi2.anki.noteeditor.web.WebEditorFieldActionAppearance
import org.xmlpull.v1.XmlPullParser

/** Stable IDs are stored per field, independently of the formatting toolbar. */
internal enum class EditorQuickAction(
    val id: String,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    val media: MultimediaActionHandler? = null,
) {
    PIN("pin", R.string.compose_editor_pin_field, R.drawable.ic_baseline_push_pin_24),
    ATTACH_MEDIA("attachMedia", CommonString.multimedia_editor_attach_tooltip, R.drawable.ic_attachment),
    EDIT_HTML("editHtml", R.string.compose_editor_source, R.drawable.ic_editor_code),
    GALLERY(
        "gallery",
        CommonString.multimedia_editor_image_field_editing_galery,
        R.drawable.ic_popup_menu_item_image,
        MultimediaActionHandler.ImageFile,
    ),
    CAMERA("camera", CommonString.multimedia_editor_image_field_editing_photo, R.drawable.ic_camera_filled, MultimediaActionHandler.Camera),
    DRAWING("drawing", CommonString.drawing, R.drawable.ic_draw_filled, MultimediaActionHandler.Drawing),
    RECORD_AUDIO(
        "recordAudio",
        CommonString.multimedia_editor_popup_audio,
        R.drawable.ic_popup_menu_item_record,
        MultimediaActionHandler.AudioRecording,
    ),
    AUDIO("audio", CommonString.multimedia_editor_popup_audio_clip, R.drawable.ic_popup_menu_item_audio, MultimediaActionHandler.AudioFile),
    VIDEO("video", CommonString.multimedia_editor_popup_video_clip, R.drawable.ic_popup_menu_item_video, MultimediaActionHandler.VideoFile),
    ;

    // Read each existing vector once, and only if enabled. No extra startup assets or duplicate icons.
    private var vector: Pair<String, String>? = null
    private var richTextVector: Pair<String, String>? = null

    fun toWeb(
        context: Context,
        pinned: Boolean,
    ): WebEditorFieldAction {
        val (path, viewBox) = vector ?: readVector(context).also { vector = it }
        val richText =
            if (this == EDIT_HTML) {
                val (richPath, richViewBox) =
                    richTextVector
                        ?: readVector(context, R.drawable.ic_editor_rich_text).also { richTextVector = it }
                WebEditorFieldActionAppearance(context.getString(R.string.compose_editor_rich_text), richPath, richViewBox)
            } else {
                null
            }
        return WebEditorFieldAction(
            id,
            context.getString(if (this == PIN && pinned) R.string.compose_editor_unpin_field else label),
            path,
            viewBox,
            if (this == PIN) pinned else null,
            richText,
        )
    }

    private fun readVector(
        context: Context,
        @DrawableRes drawable: Int = icon,
    ): Pair<String, String> {
        val namespace = "http://schemas.android.com/apk/res/android"
        val paths = mutableListOf<String>()
        var viewBox = "0 0 24 24"
        context.resources.getXml(drawable).use { xml ->
            while (xml.next() != XmlPullParser.END_DOCUMENT) {
                if (xml.eventType != XmlPullParser.START_TAG) continue
                when (xml.name) {
                    "vector" ->
                        viewBox =
                            "0 0 ${xml.getAttributeValue(namespace, "viewportWidth")} ${xml.getAttributeValue(namespace, "viewportHeight")}"
                    "path" -> paths.add(xml.getAttributeValue(namespace, "pathData"))
                }
            }
        }
        return paths.joinToString(" ") to viewBox
    }

    companion object {
        // Match the legacy editor's field controls, from start to end.
        val defaults = listOf(PIN.id, ATTACH_MEDIA.id)

        fun fromId(id: String): EditorQuickAction? = entries.firstOrNull { it.id == id }
    }
}

internal fun EditorState.fieldActions(context: Context): List<List<WebEditorFieldAction>> =
    fields.map { field -> field.toolbar.quickActions.mapNotNull { EditorQuickAction.fromId(it)?.toWeb(context, field.sticky) } }
