// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.graphics.Bitmap
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.pages.viewmodel.ImageOcclusionArgs
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.waitUntil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class ImageOcclusionTest : InstrumentedTest() {
    @Test
    fun editUsesBigIntAndSavesFields() = editAndSave()

    @Test
    fun editPreservesIdsBeyondJavaScriptNumberPrecision() = editAndSave(9007199254740993L)

    private fun editAndSave(requestedNoteId: Long? = null) {
        val image = File.createTempFile("occlusion", ".png", testContext.cacheDir)
        var noteId = 0L
        try {
            image.outputStream().use {
                val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                bitmap.recycle()
            }
            col.addImageOcclusionNoteType()
            val notetype = col.notetypes.all().first { it.isImageOcclusion }
            col.addImageOcclusionNote(
                noteTypeId = notetype.id,
                imagePath = image.path,
                occlusions = "{{c1::image-occlusion:rect:left=.1:top=.1:width=.3:height=.3}}",
                header = "original",
                backExtra = "back",
                tags = listOf("io-roundtrip"),
            )
            noteId = col.findNotes("tag:io-roundtrip").single()
            if (requestedNoteId != null) {
                col.db.execute("update notes set id = ? where id = ?", requestedNoteId, noteId)
                col.db.execute("update cards set nid = ? where nid = ?", requestedNoteId, noteId)
                noteId = requestedNoteId
            }

            val intent = ImageOcclusion.getIntent(testContext, ImageOcclusionArgs.Edit(noteId))
            ActivityScenario.launch<SingleFragmentActivity>(intent).use { scenario ->
                lateinit var page: ImageOcclusion
                scenario.onActivity { page = it.fragment as ImageOcclusion }
                waitUntil(timeout = 30.seconds, message = { "image occlusion did not load the note" }) {
                    page.evaluateJavascript(
                        "globalThis.maskEditor != null && document.getElementById('header--div')?.innerHTML === 'original'",
                    ) ==
                        "true"
                }
                assertEquals("\"$noteId\"", page.evaluateJavascript("String(anki.imageOcclusion.mode.noteId)"))
                assertEquals("\"bigint\"", page.evaluateJavascript("typeof anki.imageOcclusion.mode.noteId"))
                page.evaluateJavascript(
                    """
                    const header = document.getElementById('header--div');
                    header.innerHTML = 'updated';
                    header.dispatchEvent(new Event('input', { bubbles: true }));
                    """.trimIndent(),
                )
                page.evaluateJavascript("anki.imageOcclusion.save()")
                waitUntil(timeout = 30.seconds, message = { "image occlusion save did not finish" }) {
                    scenario.state == Lifecycle.State.DESTROYED
                }
            }
            val saved = col.getImageOcclusionNote(noteId).note
            assertEquals("<div>updated</div>", saved.header)
            assertEquals("<div>back</div>", saved.backExtra)
            assertEquals(listOf("io-roundtrip"), saved.tagsList)
            assertEquals(1, saved.occlusionsCount)
        } finally {
            col.backend.removeNotes(noteIds = listOf(noteId), cardIds = emptyList())
            image.delete()
        }
    }

    private fun PageFragment.evaluateJavascript(script: String): String {
        val result = CompletableDeferred<String>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webViewLayout.evaluateJavascript(script) { result.complete(it) }
        }
        return runBlocking { withTimeout(10.seconds) { result.await() } }
    }
}
