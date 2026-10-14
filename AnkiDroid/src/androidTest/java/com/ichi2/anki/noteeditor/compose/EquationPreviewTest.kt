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

            var selected = R.string.compose_editor_math_inline
            for (kind in listOf(
                R.string.compose_editor_math_block,
                R.string.compose_editor_latex_equation,
                R.string.compose_editor_latex_math,
                R.string.compose_editor_latex,
                R.string.compose_editor_math_chemistry,
            )) {
                checkNotNull(device.wait(Until.findObject(By.text(testContext.getString(selected))), 5_000)).click()
                checkNotNull(device.wait(Until.findObject(By.text(testContext.getString(kind))), 5_000)).click()
                selected = kind
                if (kind == R.string.compose_editor_math_chemistry) {
                    formula.text = "H2O"
                    web.awaitJavascript("!!document.querySelector('mjx-msub') && !document.querySelector('mjx-merror')")
                } else {
                    val display = kind != R.string.compose_editor_latex_equation
                    web.awaitJavascript("(document.querySelector('mjx-container')?.getAttribute('display') === 'true') === $display")
                }
            }
            capture(web, "equation-preview-${if (dark) "dark" else "light"}-chemistry")
            assertEquals(originalPage, web.evaluate("performance.timeOrigin"), "Typing and mode changes must reuse the renderer")
            assertNull(applied.get(), "Previewing must not apply an edit to the note")
            device.findObject(By.text(testContext.getString(CommonString.dialog_ok))).click()
            assertEquals(WebEditorAction.MATHJAX_CHEMISTRY to "H2O", applied.get())
        }
    }

    @Test
    fun incompleteInputRecoversAndClearingRemovesThePreview() {
        withDialog(dark = false) { web, applied ->
            web.awaitJavascript("!!document.querySelector('mjx-mfrac')")
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
            device.findObject(By.text(testContext.getString(CommonString.dialog_ok))).click()
            assertNull(applied.get(), "An empty formula must not be applied")
        }
    }

    private fun withDialog(
        dark: Boolean,
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
                                WebEditorAction.MATHJAX_INLINE,
                                WebEditorTarget("preview-test", 0, 0, 0, ""),
                                "\\frac{1}{2}",
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
