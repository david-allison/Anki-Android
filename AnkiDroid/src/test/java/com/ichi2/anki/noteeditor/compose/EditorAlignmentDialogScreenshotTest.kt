// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.content.Intent
import android.widget.TextView
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.core.widget.TextViewCompat
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.common.utils.android.getColorFromAttr
import com.ichi2.anki.noteeditor.web.WebEditorAction
import com.ichi2.anki.noteeditor.web.WebEditorTarget
import com.ichi2.compose.theme.AnkiDroidTheme
import org.junit.Rule
import org.junit.Test
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class EditorAlignmentDialogScreenshotTest : ScreenshotTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun alignmentUsesStandardListRows() {
        var applied: Pair<WebEditorAction, String>? = null
        var dismissed = 0
        val dialog = showDialog(onDismiss = { dismissed++ }, onApply = { action, value -> applied = action to value })
        composeRule.runOnIdle {
            val row = dialog.listView.getChildAt(0) as TextView
            assertEquals(
                getColorFromAttr(dialog.context, android.R.attr.colorControlNormal),
                TextViewCompat.getCompoundDrawableTintList(row)?.defaultColor,
                "Alignment icons must use the standard control colour independently of their labels",
            )
        }
        captureScreen("alignment_and_indentation")

        // Selecting a row applies immediately; automatic dismissal must not also cancel the command.
        composeRule.runOnIdle { dialog.listView.performItemClick(dialog.listView.getChildAt(4), 4, 4) }
        composeRule.waitForIdle()
        assertEquals(WebEditorAction.INDENT to "", applied)
        assertEquals(0, dismissed)
        assertFalse(dialog.isShowing)
    }

    @Test
    fun cancellingDoesNotApplyFormatting() {
        var applied: WebEditorAction? = null
        var dismissed = 0
        val dialog = showDialog(onDismiss = { dismissed++ }, onApply = { action, _ -> applied = action })
        composeRule.runOnIdle { dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick() }
        composeRule.waitForIdle()
        assertNull(applied)
        assertEquals(1, dismissed)
        assertFalse(dialog.isShowing)
    }

    private fun showDialog(
        onDismiss: () -> Unit,
        onApply: (WebEditorAction, String) -> Unit,
    ): AlertDialog {
        val activity = startActivityNormallyOpenCollectionWithIntent(AnkiActivity::class.java, Intent())
        activity.setContent {
            AnkiDroidTheme {
                EditorFormattingDialog(
                    EditorFormattingRequest(WebEditorAction.ALIGN_LEFT, WebEditorTarget("screenshot", 0, 0, 0, ""), ""),
                    onDismiss = onDismiss,
                    onApply = onApply,
                )
            }
        }
        composeRule.waitForIdle()
        return assertNotNull(ShadowDialog.getLatestDialog() as? AlertDialog)
    }
}
