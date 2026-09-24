// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.cardviewer

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.fragment.app.Fragment
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.GrantStoragePermission.storagePermission
import com.ichi2.anki.testutil.ensureWebViewIsSupported
import com.ichi2.anki.testutil.grantPermissions
import com.ichi2.anki.testutil.notificationPermission
import com.ichi2.anki.testutil.waitUntil
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TypeAnswerWebViewTest : InstrumentedTest() {
    @get:Rule
    val runtimePermissionRule = grantPermissions(storagePermission, notificationPermission)

    /** Exercises Chromium and the JavaScript bridge, which Robolectric's WebView does not run. */
    @Test
    fun suggestionsFollowTheFocusedFieldAndDoneStillSendsEnter() {
        ensureWebViewIsSupported()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val intent = SingleFragmentActivity.getIntent(testContext, Fragment::class)
        ActivityScenario.launch<SingleFragmentActivity>(intent).use { scenario ->
            lateinit var webView: RecordingWebView
            scenario.onActivity { activity ->
                webView = RecordingWebView(activity)
                activity.setContentView(webView)
                webView.settings.javaScriptEnabled = true
                webView.settings.allowFileAccess = true
                webView.loadDataWithBaseURL(
                    "file:///android_asset/",
                    """
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <input id="answer" data-ankidroid-nosuggest="true" enterkeyhint="done"
                        onkeydown="document.body.dataset.key = event.key">
                    <input id="ordinary">
                    <script src="scripts/type-answer.js"></script>
                    """.trimIndent(),
                    "text/html",
                    null,
                    null,
                )
            }
            try {
                waitUntil(message = { "Answer field did not load" }) {
                    webView.evaluate("document.getElementById('answer') !== null") == "true"
                }

                fun focus(
                    id: String,
                    noSuggest: Boolean,
                ) {
                    instrumentation.runOnMainSync {
                        webView.lastEditorInfo = null
                        webView.requestFocus()
                        webView.loadUrl("javascript:document.getElementById('$id').focus();")
                    }
                    waitUntil(message = { "Keyboard suggestions did not update for $id" }) {
                        val info = webView.lastEditorInfo ?: return@waitUntil false
                        (info.inputType == InputType.TYPE_NULL) == noSuggest
                    }
                }

                focus("answer", noSuggest = true)
                val info = assertNotNull(webView.lastEditorInfo)
                assertEquals(EditorInfo.IME_ACTION_DONE, info.imeOptions and EditorInfo.IME_MASK_ACTION)
                webView.withConnection { commitText("été", 1) }
                waitUntil(message = { "HTML answer did not receive accented text" }) {
                    webView.evaluate("document.getElementById('answer').value") == "\"été\""
                }

                focus("ordinary", noSuggest = false)
                focus("answer", noSuggest = true)
                webView.withConnection { performEditorAction(EditorInfo.IME_ACTION_DONE) }
                waitUntil(message = { "Done did not reach the HTML Enter handler" }) {
                    webView.evaluate("document.body.dataset.key") == "\"Enter\""
                }

                // Moving to another card removes the focused node without necessarily firing blur.
                webView.evaluate("document.body.innerHTML = '<input id=next>'; true")
                focus("next", noSuggest = false)
            } finally {
                instrumentation.runOnMainSync {
                    (webView.parent as? ViewGroup)?.removeView(webView)
                    webView.destroy()
                }
            }
        }
    }

    private class RecordingWebView(
        context: Context,
    ) : TypeAnswerWebView(context) {
        @Volatile
        var lastEditorInfo: EditorInfo? = null

        @Volatile
        var lastConnection: InputConnection? = null

        override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? =
            super.onCreateInputConnection(outAttrs).also { connection ->
                if (connection != null) {
                    lastConnection = connection
                    lastEditorInfo = outAttrs
                }
            }

        fun evaluate(script: String): String {
            val result = CompletableFuture<String>()
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                evaluateJavascript(script) { result.complete(it) }
            }
            return result.get(5, TimeUnit.SECONDS)
        }

        fun withConnection(block: InputConnection.() -> Unit) {
            val connection = assertNotNull(lastConnection)
            val result = CompletableFuture<Unit>()
            val handler = connection.handler ?: Handler(Looper.getMainLooper())
            handler.post {
                try {
                    connection.block()
                    result.complete(Unit)
                } catch (error: Throwable) {
                    result.completeExceptionally(error)
                }
            }
            result.get(5, TimeUnit.SECONDS)
        }
    }
}
