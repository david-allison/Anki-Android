// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose.media

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.multimedia.MultimediaActionHandler
import com.ichi2.anki.multimedia.MultimediaBottomSheet.MultimediaAction
import com.ichi2.anki.noteeditor.web.WebEditorTarget
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class PendingEditorMediaTest {
    @Test
    fun `camera destination survives recreation without carrying note fields`() {
        val target = WebEditorTarget("note-session", 3, 1, 17, "{\"startPath\":[0],\"start\":4}")
        val pending = PendingEditorMedia(target, MultimediaAction.OPEN_CAMERA)

        val json = JSONObject(pending.toJson().toString())
        val restored = PendingEditorMedia.fromJson(json)

        assertEquals(target, restored.target)
        assertEquals(MultimediaAction.OPEN_CAMERA, restored.action)
        assertNull(restored.result)
        assertFalse(json.has("fields"))
    }

    @Test
    fun `returned media can resume import after recreation`() {
        for (action in MultimediaAction.entries) {
            val field =
                MultimediaActionHandler.forAction(action).createField().apply {
                    mediaFile = File("/tmp/editor-camera-result.png")
                    hasTemporaryMedia = true
                }
            val pending = PendingEditorMedia(WebEditorTarget("session", 0, 0, 2, "{}"), action, field)

            val restored = PendingEditorMedia.fromJson(JSONObject(pending.toJson().toString()))
            val restoredField = requireNotNull(restored.result)

            assertEquals(pending.target, restored.target)
            assertEquals(field.type, restoredField.type)
            assertEquals(field.mediaFile, restoredField.mediaFile)
            assertTrue(restoredField.hasTemporaryMedia)
        }
    }
}
