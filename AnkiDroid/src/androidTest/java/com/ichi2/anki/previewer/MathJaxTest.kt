// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.previewer

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.R
import com.ichi2.anki.browser.IdsFile
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.waitUntil
import com.ichi2.anki.workarounds.SafeWebViewLayout
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class MathJaxTest : InstrumentedTest() {
    @Test
    fun mathLoadsOnlyWhenNeededAndIsReusedAcrossCards() {
        val note = addNoteUsingBasicNoteType("question without math", "answer without math")
        val ids = IdsFile(testContext.cacheDir, listOf(note.firstCard(col).id))
        try {
            ActivityScenario.launch<CardViewerActivity>(PreviewerFragment.getIntent(testContext, ids, 0)).use { scenario ->
                lateinit var page: PreviewerFragment
                scenario.onActivity { page = it.fragment as PreviewerFragment }
                page.waitFor("document.getElementById('qa')?.textContent.includes('question without math') === true")
                assertEquals("0", page.evaluateJavascript("document.querySelectorAll('script[src*=mathjax]').length"))

                // Preload answer-only math while the plain question is displayed.
                val math = JSONObject.quote("\\(x + 1\\)")
                page.evaluateJavascript("_showQuestion('plain question', $math, '');")
                page.waitFor("globalThis.MathJax?.startup?.document != null")
                page.evaluateJavascript("_showAnswer($math, '');")
                page.waitFor("document.querySelector('#qa mjx-container') !== null")
                assertEquals("2", page.evaluateJavascript("document.querySelectorAll('script[src*=mathjax]').length"))

                // A following plain card and another math card reuse the loaded scripts.
                page.evaluateJavascript("_showQuestion('next plain question', '', '');")
                page.waitFor("document.getElementById('qa')?.textContent === 'next plain question'")
                page.evaluateJavascript("_showQuestion($math, '', '');")
                page.waitFor("document.querySelector('#qa mjx-container') !== null")
                assertEquals("2", page.evaluateJavascript("document.querySelectorAll('script[src*=mathjax]').length"))
            }
        } finally {
            col.backend.removeNotes(noteIds = listOf(note.id), cardIds = emptyList())
        }
    }

    private fun PreviewerFragment.waitFor(script: String) {
        waitUntil(timeout = 30.seconds, message = { "MathJax page condition failed: $script" }) {
            evaluateJavascript(script) == "true"
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
