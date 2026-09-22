// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.previewer

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.R
import com.ichi2.anki.browser.IdsFile
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.waitUntil
import com.ichi2.anki.workarounds.SafeWebViewLayout
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class CardMediaTest : InstrumentedTest() {
    @Test
    fun mediaFramesKeepTheirPresentationButCannotExecuteScripts() {
        val mediaDir = CollectionHelper.getMediaDirectory(testContext)
        val html = File(mediaDir, "_frame-test.html")
        val css = File(mediaDir, "_frame-test.css")
        html.writeText(
            """<link rel="stylesheet" href="_frame-test.css"><script>parent.mediaScriptExecuted = true</script><p id="message">media</p>""",
        )
        css.writeText("#message { color: rgb(1, 2, 3); }")
        val note =
            addNoteUsingBasicNoteType(
                """<script>window.cardScriptExecuted = true</script><iframe id="media" src="_frame-test.html"></iframe>""",
            )
        val ids = IdsFile(testContext.cacheDir, listOf(note.firstCard(col).id))
        try {
            ActivityScenario.launch<CardViewerActivity>(PreviewerFragment.getIntent(testContext, ids, 0)).use { scenario ->
                lateinit var page: PreviewerFragment
                scenario.onActivity { page = it.fragment as PreviewerFragment }
                waitUntil(timeout = 30.seconds, message = { "embedded media did not load its stylesheet" }) {
                    page.evaluateJavascript(
                        """
                        (() => {
                            const frame = document.getElementById('media');
                            const message = frame?.contentDocument?.getElementById('message');
                            return message != null && frame.contentWindow.getComputedStyle(message).color === 'rgb(1, 2, 3)';
                        })()
                        """.trimIndent(),
                    ) == "true"
                }
                assertEquals("true", page.evaluateJavascript("globalThis.cardScriptExecuted === true"))
                assertEquals("false", page.evaluateJavascript("globalThis.mediaScriptExecuted === true"))
            }
        } finally {
            col.backend.removeNotes(noteIds = listOf(note.id), cardIds = emptyList())
            html.delete()
            css.delete()
        }
    }

    private fun PreviewerFragment.evaluateJavascript(script: String): String {
        val result = CompletableDeferred<String>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            requireView().findViewById<SafeWebViewLayout>(R.id.web_view_layout).evaluateJavascript(script) { result.complete(it) }
        }
        return runBlocking { withTimeout(10.seconds) { result.await() } }
    }
}
