// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.fragment.app.Fragment
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.noteeditor.web.WebEditorAction
import com.ichi2.anki.noteeditor.web.WebEditorTarget
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.TestInputMethodRule
import com.ichi2.anki.testutil.awaitJavascript
import com.ichi2.anki.testutil.ensureWebViewIsSupported
import com.ichi2.anki.testutil.evaluate
import com.ichi2.anki.testutil.waitUntil
import com.ichi2.compose.theme.AnkiDroidTheme
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Render the real offline MathJax bundle inside the native equation dialog. */
class EquationPreviewTest : InstrumentedTest() {
    @get:Rule
    val keyboard = TestInputMethodRule()

    @Test
    fun previewFollowsTypingAndEquationKindInLightTheme() = previewFollowsTypingAndEquationKind(dark = false)

    @Test
    fun previewFollowsTypingAndEquationKindInDarkTheme() = previewFollowsTypingAndEquationKind(dark = true)

    private fun previewFollowsTypingAndEquationKind(dark: Boolean) {
        withDialog(dark) { web, applied ->
            val device = UiDevice.getInstance(getInstrumentation())
            web.awaitJavascript("document.querySelector('mjx-mfrac')?.getBoundingClientRect().height > 0")
            val originalPage = web.evaluate("performance.timeOrigin")
            val formula = checkNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5_000))
            formula.click()
            formula.text = "x^2"
            web.awaitJavascript("!!document.querySelector('mjx-msup') && !document.querySelector('mjx-mfrac')")
            assertTrue(formula.isFocused, "Live rendering must not take focus from the formula input")
            capture(web, "equation-preview-${if (dark) "dark" else "light"}-keyboard")
            device.pressBack()

            assertTrue(device.hasObject(By.text(testContext.getString(R.string.compose_editor_math_block))))
            assertTrue(device.hasObject(By.text(testContext.getString(R.string.compose_editor_math_chemistry))))
            assertTrue(!device.hasObject(By.textContains("LaTeX")), "Only MathJax formats should be offered")
            for (kind in listOf(
                R.string.compose_editor_math_block,
                R.string.compose_editor_math_inline,
                R.string.compose_editor_math_chemistry,
            )) {
                checkNotNull(device.wait(Until.findObject(By.text(testContext.getString(kind))), 5_000)).click()
                if (kind == R.string.compose_editor_math_chemistry) {
                    formula.text = "H2O"
                    web.awaitJavascript("!!document.querySelector('mjx-msub') && !document.querySelector('mjx-merror')")
                    web.awaitJavascript(
                        """
                        (() => {
                            const math = document.querySelector('#preview mjx-assistive-mml')?.cloneNode(true);
                            math?.querySelectorAll('mphantom').forEach(node => node.remove());
                            return math?.textContent === 'H2O';
                        })()
                        """.trimIndent(),
                    )
                } else {
                    val display = kind == R.string.compose_editor_math_block
                    web.awaitJavascript("(document.querySelector('mjx-container')?.getAttribute('display') === 'true') === $display")
                }
            }
            capture(web, "equation-preview-${if (dark) "dark" else "light"}-chemistry")
            assertEquals(originalPage, web.evaluate("performance.timeOrigin"), "Typing and mode changes must reuse the renderer")
            assertNull(applied.get(), "Previewing must not apply an edit to the note")
            device.findObject(By.text(testContext.getString(CommonString.dialog_ok))).click()
            waitUntil(message = { "The formula was not applied" }) { applied.get() != null }
            assertEquals(WebEditorAction.MATHJAX_CHEMISTRY to "H2O", applied.get())
        }
    }

    @Test
    fun incompleteInputRecoversAndClearingRemovesThePreview() {
        withDialog(dark = false) { web, applied ->
            web.awaitJavascript("!!document.querySelector('mjx-mfrac')")
            val originalPage = web.evaluate("performance.timeOrigin")
            val device = UiDevice.getInstance(getInstrumentation())
            val formula = checkNotNull(device.findObject(By.clazz("android.widget.EditText")))
            formula.text = "\\frac{"
            web.awaitJavascript("!!document.querySelector('mjx-merror')")
            formula.text = "\\frac{1}{2}"
            formula.text = "x^2"
            web.awaitJavascript("!!document.querySelector('mjx-msup') && !document.querySelector('mjx-merror')")
            formula.text = "</script><img src=x onerror='window.injected=true'>"
            web.awaitJavascript(
                "document.querySelector('#preview mjx-assistive-mml')?.textContent.includes('window.injected=true') === true",
            )
            assertEquals("false", web.evaluate("!!window.injected || !!document.querySelector('#preview img')"))
            formula.text = ""
            web.awaitJavascript("document.getElementById('preview').childElementCount === 0")
            val placeholder = By.text(testContext.getString(R.string.compose_editor_equation_preview_empty))
            assertTrue(device.wait(Until.hasObject(placeholder), 5_000))
            device.findObject(By.text(testContext.getString(CommonString.dialog_ok))).click()
            assertNull(applied.get(), "An empty formula must not be applied")
            formula.text = "x^2"
            web.awaitJavascript("!!document.querySelector('mjx-msup')")
            assertTrue(device.wait(Until.gone(placeholder), 5_000))
            assertEquals(originalPage, web.evaluate("performance.timeOrigin"), "Clearing the formula must retain the renderer")
        }
    }

    @Test
    fun wideBlockEquationCanBeReadFromBeginningToEnd() {
        val equation = (1..40).joinToString(" + ") { "x_{$it}" } + " = y"
        withDialog(dark = false, text = equation, action = WebEditorAction.MATHJAX_BLOCK) { web, applied ->
            web.awaitJavascript("!!document.querySelector('mjx-math')")
            web.awaitJavascript("document.getElementById('preview').scrollWidth > document.getElementById('preview').clientWidth * 2")
            assertEquals(
                "true",
                web.evaluate("document.querySelector('mjx-math').getBoundingClientRect().left >= 0"),
                "The beginning of a wide block equation must not be clipped off the left edge",
            )
            capture(web, "equation-preview-wide-start")
            val bounds = checkNotNull(UiDevice.getInstance(getInstrumentation()).findObject(By.clazz(WebView::class.java))).visibleBounds
            UiDevice.getInstance(getInstrumentation()).swipe(bounds.right - 24, bounds.centerY(), bounds.left + 24, bounds.centerY(), 20)
            web.awaitJavascript("document.getElementById('preview').scrollLeft > 0")
            web.evaluate("document.getElementById('preview').scrollLeft = 100000")
            web.awaitJavascript("document.querySelector('mjx-math').getBoundingClientRect().right <= visualViewport.width + 1")
            capture(web, "equation-preview-wide-end")
            assertNull(applied.get())
        }
    }

    @Test
    fun tallMatrixCanBePannedToItsLastRow() {
        val matrix = "\\begin{bmatrix}" + (1..16).joinToString(" \\\\ ") { "$it & ${it + 1} & ${it + 2}" } + "\\end{bmatrix}"
        withDialog(dark = false, text = matrix, action = WebEditorAction.MATHJAX_BLOCK) { web, applied ->
            web.awaitJavascript("document.querySelectorAll('mjx-mtr').length === 16")
            web.awaitJavascript("document.getElementById('preview').scrollHeight > document.getElementById('preview').clientHeight")
            capture(web, "equation-preview-matrix-top")
            val bounds = checkNotNull(UiDevice.getInstance(getInstrumentation()).findObject(By.clazz(WebView::class.java))).visibleBounds
            UiDevice.getInstance(getInstrumentation()).swipe(bounds.centerX(), bounds.bottom - 24, bounds.centerX(), bounds.top + 24, 20)
            web.awaitJavascript("document.getElementById('preview').scrollTop > 0")
            web.evaluate("document.getElementById('preview').scrollTop = 100000")
            web.awaitJavascript("document.querySelector('mjx-math').getBoundingClientRect().bottom <= visualViewport.height + 1")
            capture(web, "equation-preview-matrix-bottom")
            assertNull(applied.get())
        }
    }

    @Test
    fun editingPreservesTheRendererAndPendingText() {
        withDialog(dark = false) { web, applied ->
            web.awaitJavascript("!!document.querySelector('mjx-mfrac')")
            val originalPage = web.evaluate("performance.timeOrigin")
            val device = UiDevice.getInstance(getInstrumentation())
            val formula = checkNotNull(device.findObject(By.clazz("android.widget.EditText")))
            formula.click()
            formula.text = "\\int_{-\\infty}^{\\infty} e^{-x^2} \\, dx = \\sqrt{\\pi}"
            web.awaitJavascript("!!document.querySelector('mjx-msqrt')")
            assertTrue(formula.isFocused)
            capture(web, "equation-preview-expanded-focused-input")
            device.pressBack()
            capture(web, "equation-preview-expanded")
            assertEquals(originalPage, web.evaluate("performance.timeOrigin"), "Editing must retain the renderer")
            assertNull(applied.get(), "Editing must not apply the equation")
            device.findObject(By.text(testContext.getString(CommonString.dialog_ok))).click()
            waitUntil(message = { "The expanded editor did not apply its equation" }) { applied.get() != null }
            assertEquals(WebEditorAction.MATHJAX_INLINE to formula.text, applied.get())
        }
    }

    private fun withDialog(
        dark: Boolean,
        text: String = "\\frac{1}{2}",
        action: WebEditorAction = WebEditorAction.MATHJAX_INLINE,
        block: (WebView, AtomicReference<Pair<WebEditorAction, String>>) -> Unit,
    ) {
        ensureWebViewIsSupported()
        val applied = AtomicReference<Pair<WebEditorAction, String>>()
        ActivityScenario.launch<SingleFragmentActivity>(SingleFragmentActivity.getIntent(testContext, Fragment::class)).use { scenario ->
            scenario.onActivity { activity ->
                activity.setTheme(if (dark) R.style.Theme_Dark else R.style.Theme_Light)
                activity.setContent {
                    AnkiDroidTheme {
                        EditorFormattingDialog(
                            EditorFormattingRequest(
                                action,
                                WebEditorTarget("preview-test", 0, 0, 0, ""),
                                text,
                            ),
                            onDismiss = {},
                            onApply = { action, text -> applied.set(action to text) },
                        )
                    }
                }
            }
            lateinit var web: WebView
            onView(isAssignableFrom(WebView::class.java)).inRoot(isDialog()).check { view, error ->
                if (error != null) throw error
                web = view as WebView
            }
            block(web, applied)
        }
    }

    private fun capture(
        web: WebView,
        name: String,
    ) {
        web.awaitJavascript("document.getElementById('preview').clientHeight >= visualViewport.height - 1")
        web.awaitJavascript("document.fonts.status === 'loaded'")
        val rendered = CountDownLatch(1)
        getInstrumentation().runOnMainSync {
            web.postVisualStateCallback(
                0,
                object : WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) {
                        web.postOnAnimation { web.postOnAnimation { rendered.countDown() } }
                    }
                },
            )
        }
        assertTrue(rendered.await(10, TimeUnit.SECONDS))
        assertTrue(UiDevice.getInstance(getInstrumentation()).takeScreenshot(File(testContext.filesDir, "$name.png")))
    }
}
