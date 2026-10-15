// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextReplacement
import androidx.core.net.toUri
import com.ichi2.anki.AnkiActivity
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

        block.performClick().assertIsOn()
        inline.assertIsOff()
        chemistry.assertIsOff()
        captureScreen("add_mathjax_equation_block")
        // One format must remain selected, even when the active button is tapped again.
        block.performClick().assertIsOn()

        chemistry.performClick().assertIsOn()
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

    private fun showDialog(
        editing: Boolean = false,
        text: String = "x^2",
    ): AnkiActivity {
        val activity = startActivityNormallyOpenCollectionWithIntent(AnkiActivity::class.java, Intent())
        activity.setContent {
            AnkiDroidTheme {
                EditorFormattingDialog(
                    EditorFormattingRequest(
                        WebEditorAction.MATHJAX_INLINE,
                        WebEditorTarget("screenshot", 0, 0, 0, ""),
                        text,
                        editingEquation = editing,
                    ),
                    onDismiss = {},
                    onApply = { _, _ -> },
                )
            }
        }
        composeRule.waitForIdle()
        return activity
    }
}
