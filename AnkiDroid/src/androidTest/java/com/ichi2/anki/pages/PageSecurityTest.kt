// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.content.Intent
import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.common.destinations.StatisticsDestination
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

class PageSecurityTest : InstrumentedTest() {
    @Test
    fun trustedPagesCanExecuteBridgeLinks() {
        withPage(
            StatisticsDestination.toIntent(testContext),
            "document.getElementById('statisticsSearchText') !== null",
        ) {
            clickJavascriptLink()
            assertEquals("true", evaluateJavascript("globalThis.linkExecuted === true"))
        }
    }

    @Test
    fun imageOcclusionStartsButBlocksScriptsInNoteContent() {
        val image = File.createTempFile("page-security", ".png", testContext.cacheDir)
        try {
            image.outputStream().use {
                val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                bitmap.recycle()
            }
            withPage(
                ImageOcclusion.getIntent(testContext, ImageOcclusionArgs.Add(image.path, 0, col.decks.current().id)),
                "document.querySelector('.editor-container canvas') !== null && typeof anki?.imageOcclusion?.save === 'function'",
            ) {
                clickJavascriptLink()
                assertEquals("false", evaluateJavascript("globalThis.linkExecuted === true"))
                evaluateJavascript(
                    """
                    const button = document.createElement('button');
                    button.setAttribute('onclick', 'globalThis.inlineExecuted = true');
                    document.body.append(button);
                    button.click();
                    """.trimIndent(),
                )
                assertEquals("false", evaluateJavascript("globalThis.inlineExecuted === true"))
            }
        } finally {
            image.delete()
        }
    }

    private fun PageFragment.clickJavascriptLink() {
        evaluateJavascript(
            """
            const link = document.createElement('a');
            link.href = 'javascript:globalThis.linkExecuted = true; void(0)';
            document.body.append(link);
            link.click();
            """.trimIndent(),
        )
        // javascript: navigation executes asynchronously, after the click's script returns.
        evaluateJavascript("document.readyState")
    }

    private fun withPage(
        intent: Intent,
        readyScript: String,
        block: PageFragment.() -> Unit,
    ) {
        ActivityScenario.launch<SingleFragmentActivity>(intent).use { scenario ->
            lateinit var page: PageFragment
            scenario.onActivity { page = it.fragment as PageFragment }
            waitUntil(timeout = 30.seconds, message = { "page did not finish loading: $readyScript" }) {
                page.evaluateJavascript(readyScript) == "true"
            }
            page.block()
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
