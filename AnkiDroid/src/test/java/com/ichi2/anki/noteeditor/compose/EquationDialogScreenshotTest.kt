// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextReplacement
import androidx.core.net.toUri
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.noteeditor.web.WebEditorAction
import com.ichi2.anki.noteeditor.web.WebEditorTarget
import com.ichi2.compose.theme.AnkiDroidTheme
import com.ichi2.testutils.assertOpenedUrl
import com.ichi2.testutils.registerWebBrowser
import org.junit.Rule
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals

/** Native dialog captures; EquationPreviewTest covers real MathJax rendering in a WebView. */
class EquationDialogScreenshotTest : ScreenshotTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    override fun applyDeviceConfig() {
        // Wider qualifiers make Compose dialogs containing text fields loop during measurement.
        // https://github.com/robolectric/robolectric/issues/8460
        RuntimeEnvironment.setQualifiers("w320dp-h640dp-mdpi")
    }

    @Test
    fun addEquation() {
        showDialog()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_mathjax_equation)).assertIsDisplayed()
        val inline = composeRule.onNodeWithText(getResourceString(R.string.compose_editor_math_inline))
        val block = composeRule.onNodeWithText(getResourceString(R.string.compose_editor_math_block))
        val chemistry = composeRule.onNodeWithText(getResourceString(R.string.compose_editor_math_chemistry))
        inline.assertIsOn()
        captureScreen("add_mathjax_equation")

        // Capture selection states without Android ripples, which use a separate animation clock.
        block.performSemanticsAction(SemanticsActions.OnClick).assertIsOn()
        inline.assertIsOff()
        chemistry.assertIsOff()
        captureScreen("add_mathjax_equation_block")
        // One format must remain selected, even when the active button is tapped again.
        block.performSemanticsAction(SemanticsActions.OnClick).assertIsOn()

        chemistry.performSemanticsAction(SemanticsActions.OnClick).assertIsOn()
        block.assertIsOff()
        inline.assertIsOff()
        composeRule.onNodeWithText("x^2").assertIsDisplayed()
        captureScreen("add_mathjax_equation_chemistry")
    }

    @Test
    fun editEquation() {
        showDialog(editing = true)
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_mathjax_equation)).assertIsDisplayed()
        captureScreen("edit_mathjax_equation")
    }

    @Test
    fun emptyPreviewExplainsWhatToEnter() {
        showDialog(text = "")
        val placeholder = composeRule.onNodeWithText(getResourceString(R.string.compose_editor_equation_preview_empty))
        val formula = composeRule.onNodeWithText(getResourceString(R.string.compose_editor_formula))
        placeholder.assertIsDisplayed()
        captureScreen("empty_mathjax_equation")

        formula.performTextReplacement("x^2")
        placeholder.assertDoesNotExist()
        formula.performTextClearance()
        placeholder.assertIsDisplayed()
        formula.performTextReplacement("  ")
        placeholder.assertIsDisplayed()
    }

    @Test
    fun equationHelpOpensTheManual() {
        val activity = showDialog()
        activity.registerWebBrowser()
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_math_help)).performClick()
        activity.assertOpenedUrl("https://docs.ankiweb.net/manual/math#mathjax".toUri())
        composeRule.onNodeWithText("x^2").assertIsDisplayed()
    }

    @Test
    fun longEquationCanExpandAndApplyWithoutTruncation() {
        val equation = (1..24).joinToString(" + ") { "\\frac{x_{$it}}{$it}" } + " = y"
        var applied: Pair<WebEditorAction, String>? = null
        showDialog(text = equation, action = WebEditorAction.MATHJAX_BLOCK, onApply = { action, text -> applied = action to text })
        captureScreen("long_equation_compact")
        expand()
        captureScreen("long_equation_expanded_source")
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_equation_collapse)).performScrollTo().performClick()
        composeRule.onNodeWithText(getResourceString(CommonString.dialog_ok)).assertIsDisplayed().performClick()
        assertEquals(WebEditorAction.MATHJAX_BLOCK to equation, applied)
    }

    @Test
    fun multilineEquationInExpandedEditor() {
        showDialog(text = multilineEquation, action = WebEditorAction.MATHJAX_BLOCK)
        expand()
        captureScreen("multiline_equation_expanded_source")
        composeRule.onNodeWithTag("equation_preview").performScrollTo()
        composeRule.waitForIdle()
        captureScreen("multiline_equation_expanded_preview")
        composeRule.onNodeWithText(getResourceString(CommonString.dialog_ok)).assertIsDisplayed()
    }

    @Test
    fun longEquationWithLargeText() {
        RuntimeEnvironment.setFontScale(1.5f)
        showDialog(text = multilineEquation, action = WebEditorAction.MATHJAX_BLOCK)
        expand()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_formula)).performScrollTo()
        captureScreen("long_equation_large_text_source")
        composeRule.onNodeWithTag("equation_preview").performScrollTo()
        composeRule.waitForIdle()
        captureScreen("long_equation_large_text_preview")
        composeRule.onNodeWithText(getResourceString(CommonString.dialog_ok)).assertIsDisplayed()
    }

    @Test
    fun longEquationInShortWindow() {
        RuntimeEnvironment.setQualifiers("+h400dp")
        showDialog(text = multilineEquation, action = WebEditorAction.MATHJAX_BLOCK)
        expand()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_formula)).performScrollTo()
        captureScreen("long_equation_short_window_source")
        composeRule.onNodeWithTag("equation_preview").performScrollTo()
        composeRule.waitForIdle()
        captureScreen("long_equation_short_window_preview")
        composeRule.onNodeWithText(getResourceString(CommonString.dialog_ok)).assertIsDisplayed()
    }

    private fun expand() {
        composeRule
            .onNodeWithText(getResourceString(R.string.compose_editor_equation_expand))
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_equation_collapse)).assertExists()
        composeRule.waitForIdle()
    }

    private val multilineEquation =
        """
        \begin{aligned}
        f(x) &= \sum_{n=0}^{\infty} \frac{x^n}{n!} \\
             &= 1 + x + \frac{x^2}{2!} \\
             &\quad + \frac{x^3}{3!} \\
             &\quad + \frac{x^4}{4!} \\
             &\quad + \cdots \\
             &= e^x
        \end{aligned}
        """.trimIndent()

    private fun showDialog(
        editing: Boolean = false,
        text: String = "x^2",
        action: WebEditorAction = WebEditorAction.MATHJAX_INLINE,
        onApply: (WebEditorAction, String) -> Unit = { _, _ -> },
    ): AnkiActivity {
        val activity = startActivityNormallyOpenCollectionWithIntent(AnkiActivity::class.java, Intent())
        activity.setContent {
            AnkiDroidTheme {
                EditorFormattingDialog(
                    EditorFormattingRequest(
                        action,
                        WebEditorTarget("screenshot", 0, 0, 0, ""),
                        text,
                        editingEquation = editing,
                    ),
                    onDismiss = {},
                    onApply = onApply,
                )
            }
        }
        composeRule.waitForIdle()
        return activity
    }
}
