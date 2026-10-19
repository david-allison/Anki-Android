// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.web

import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.test.core.app.ActivityScenario
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.ensureWebViewIsSupported
import com.ichi2.anki.testutil.evaluate
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Exercise Chromium's real formatting engine without changing the device keyboard or permissions. */
class WebEditorFormattingTest : InstrumentedTest() {
    @Test
    fun equationAlignmentPreservesItsCapturedFieldAndHistory() =
        withEditor {
            for ((kind, formula, stored) in listOf(
                Triple(WebEditorAction.MATHJAX_INLINE, "x^2", "\\(x^2\\)"),
                Triple(WebEditorAction.MATHJAX_BLOCK, "x^2", "\\[x^2\\]"),
                Triple(WebEditorAction.MATHJAX_CHEMISTRY, "H2O", "\\(\\ce{H2O}\\)"),
            )) {
                loadDocument(document())
                focusField(0)
                assertTrue(execute(kind, value = formula))
                val before = snapshot().fields
                val target = assertNotNull(captureTarget())
                // A native dialog may take focus away before applying its captured command.
                focusField(1)
                assertTrue(execute(WebEditorAction.ALIGN_CENTER, target))
                val aligned = snapshot().fields
                assertEquals(2, aligned[0].split(stored).size, "$kind: formula must appear exactly once")
                assertEquals("untouched", aligned[1])
                assertEquals("1", evaluate("$field.querySelectorAll('anki-mathjax').length"))
                assertEquals("\"center\"", evaluate("getComputedStyle($field.querySelector('anki-frame')).textAlign"))
                assertEquals("0", evaluate("$field.querySelectorAll('anki-frame[contenteditable]').length"))

                assertTrue(execute(WebEditorAction.UNDO))
                assertEquals(before, snapshot().fields)
                assertTrue(execute(WebEditorAction.REDO))
                assertEquals(aligned, snapshot().fields)
                assertTrue(execute(WebEditorAction.SOURCE_MODE))
                assertTrue(execute(WebEditorAction.SOURCE_MODE))
                assertEquals(aligned, snapshot().fields)
                assertEquals("1", evaluate("$field.querySelectorAll('anki-mathjax').length"))
            }
        }

    @Test
    fun listsAndInlineFormattingPreserveEquationAndSurroundingHtml() =
        withEditor {
            val html = "<b>before</b> <a href=\"https://example.com\">link</a> \\(x^2\\) after"
            for (action in listOf(WebEditorAction.BULLETED_LIST, WebEditorAction.NUMBERED_LIST)) {
                loadDocument(document().copy(fields = listOf(WebEditorField("Front", html), WebEditorField("Back", "untouched"))))
                focusField(0)
                evaluate(
                    """
                    (() => {
                        const field = $field;
                        const range = new Range();
                        range.selectNodeContents(field);
                        const selection = field.getRootNode().getSelection();
                        selection.removeAllRanges();
                        selection.addRange(range);
                    })()
                    """.trimIndent(),
                )
                assertTrue(execute(WebEditorAction.ITALIC))
                val before = snapshot().fields
                assertTrue(execute(action))
                val listed = snapshot().fields
                assertEquals(2, listed[0].split("\\(x^2\\)").size)
                assertTrue(listed[0].contains("before") && listed[0].contains("after"))
                assertEquals("untouched", listed[1])
                assertEquals("1", evaluate("$field.querySelectorAll('li anki-mathjax').length"))
                assertEquals("\"https://example.com\"", evaluate("$field.querySelector('a').getAttribute('href')"))
                assertTrue(execute(WebEditorAction.UNDO))
                assertEquals(before, snapshot().fields)
                assertTrue(execute(WebEditorAction.REDO))
                assertEquals(listed, snapshot().fields)
            }
        }

    @Test
    fun equationListIndentationPreservesItsCapturedFieldAndHistory() =
        withEditor {
            for (formula in listOf("\\(x^2\\)", "\\[x^2\\]", "\\(\\ce{H2O}\\)")) {
                for (list in listOf("ul", "ol")) {
                    val html = "<$list><li>first</li><li>$formula</li></$list>"
                    loadDocument(document().copy(fields = listOf(WebEditorField("Front", html), WebEditorField("Back", "untouched"))))
                    focusField(0)
                    evaluate(
                        """
                        (() => {
                            const field = $field;
                            const handle = field.querySelector('frame-start');
                            field.getRootNode().getSelection().setPosition(handle.firstChild, 0);
                        })()
                        """.trimIndent(),
                    )
                    for (action in listOf(WebEditorAction.INDENT, WebEditorAction.OUTDENT)) {
                        val before = snapshot().fields
                        val target = assertNotNull(captureTarget())
                        focusField(1)
                        assertTrue(execute(action, target))
                        val after = snapshot().fields
                        assertEquals(2, after[0].split(formula).size, "$action $list: formula must appear exactly once")
                        assertEquals("untouched", after[1])
                        val nested = if (action == WebEditorAction.INDENT) "1" else "0"
                        assertEquals(nested, evaluate("$field.querySelectorAll('$list $list anki-mathjax').length"))
                        assertEquals("2", evaluate("$field.querySelectorAll('li').length"))
                        assertEquals("1", evaluate("$field.querySelectorAll('anki-mathjax').length"))
                        assertEquals("\"false\"", evaluate("$field.querySelector('anki-mathjax').getAttribute('contenteditable')"))
                        assertTrue(execute(WebEditorAction.UNDO))
                        assertEquals(before, snapshot().fields)
                        assertTrue(execute(WebEditorAction.REDO))
                        assertEquals(after, snapshot().fields)
                    }
                    val after = snapshot().fields
                    assertTrue(execute(WebEditorAction.SOURCE_MODE))
                    assertTrue(execute(WebEditorAction.SOURCE_MODE))
                    assertEquals(after, snapshot().fields)
                }
            }
        }

    private fun document() =
        WebEditorDocument("formatting-test", 0, listOf(WebEditorField("Front", ""), WebEditorField("Back", "untouched")))

    private fun withEditor(block: suspend WebEditorView.() -> Unit) {
        ensureWebViewIsSupported()
        ActivityScenario.launch<SingleFragmentActivity>(SingleFragmentActivity.getIntent(testContext, Fragment::class)).use { scenario ->
            lateinit var web: WebEditorView
            scenario.onActivity { activity ->
                web = WebEditorView(activity, activity.cacheDir)
                activity.setContentView(web)
            }
            try {
                runBlocking { web.block() }
            } finally {
                scenario.onActivity {
                    (web.parent as? ViewGroup)?.removeView(web)
                    web.destroy()
                }
            }
        }
    }

    companion object {
        private const val field =
            "document.querySelector('.field-container[data-index=\"0\"] .rich-text-editable').shadowRoot.querySelector('anki-editable')"
    }
}
