// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_add_equation)).assertIsDisplayed()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_math_inline)).assertIsSelected()
        captureScreen("add_mathjax_equation")
    }

    @Test
    fun editEquation() {
        showDialog(editing = true)
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_edit_equation)).assertIsDisplayed()
        captureScreen("edit_mathjax_equation")
    }

    @Test
    fun equationHelpOpensTheManual() {
        val activity = showDialog()
        activity.registerWebBrowser()
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_math_help)).performClick()
        activity.assertOpenedUrl("https://docs.ankiweb.net/manual/math#mathjax".toUri())
        composeRule.onNodeWithText("x^2").assertIsDisplayed()
    }

    private fun showDialog(editing: Boolean = false): AnkiActivity {
        val activity = startActivityNormallyOpenCollectionWithIntent(AnkiActivity::class.java, Intent())
        activity.setContent {
            AnkiDroidTheme {
                EditorFormattingDialog(
                    EditorFormattingRequest(
                        WebEditorAction.MATHJAX_INLINE,
                        WebEditorTarget("screenshot", 0, 0, 0, ""),
                        "x^2",
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
