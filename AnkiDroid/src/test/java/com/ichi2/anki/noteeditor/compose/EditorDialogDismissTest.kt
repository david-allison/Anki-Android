// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.content.Intent
import android.view.MotionEvent
import androidx.activity.ComponentDialog
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.noteeditor.web.WebEditorAction
import com.ichi2.anki.noteeditor.web.WebEditorTarget
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.compose.theme.AnkiDroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w320dp-h640dp-mdpi")
class EditorDialogDismissTest : RobolectricTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()
    private val field = EditorFieldState("Front", "", "Arial", 20, false, null, false, false, false, description = "Original")
    private var dismissed = false
    private var saved: String? = null

    @Test
    fun `unchanged description closes without confirmation`() {
        showDescription()
        cancel()
        assertTrue(dismissed)
        composeRule.onNodeWithText(TR.cardTemplatesDiscardChanges()).assertDoesNotExist()
    }

    @Test
    fun `description can be kept and saved after attempting to dismiss`() {
        showDescription()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("An unfinished hint")
        cancel()
        assertFalse(dismissed)
        assertNull(saved)
        keepEditing()
        composeRule.onNodeWithText("An unfinished hint").assertExists()
        composeRule.onNodeWithText(getResourceString(CommonString.save)).performClick()
        assertEquals("An unfinished hint", saved)
    }

    @Test
    fun `discarding a cleared description requires confirmation`() {
        showDescription()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("")
        cancel()
        assertFalse(dismissed)
        discard()
        assertTrue(dismissed)
        assertNull(saved)
    }

    @Test
    fun `reverting description edits removes the confirmation`() {
        showDescription()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("Changed")
        composeRule.onNode(hasSetTextAction()).performTextReplacement("Original")
        cancel()
        assertTrue(dismissed)
    }

    @Test
    fun `back and outside taps protect edits and dismissing confirmation keeps them`() {
        showDescription()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("Do not lose this")
        val dialog = ShadowDialog.getLatestDialog() as ComponentDialog
        composeRule.runOnIdle { dialog.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithText(TR.cardTemplatesDiscardChanges()).assertExists()
        assertFalse(dismissed)
        composeRule.runOnIdle { (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithText("Do not lose this").assertExists()
        assertFalse(dismissed)
        composeRule.runOnIdle {
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(0, 0, action, -1f, -1f, 0)
                try {
                    dialog.onTouchEvent(event)
                } finally {
                    event.recycle()
                }
            }
        }
        composeRule.onNodeWithText(TR.cardTemplatesDiscardChanges()).assertExists()
        assertFalse(dismissed)
        discard()
        assertTrue(dismissed)
        assertNull(saved)
    }

    @Test
    fun `quick action changes apply immediately and Back closes without a discard prompt`() {
        show {
            EditorQuickActionsDialog(
                EditorFieldTarget("test", 0, 0),
                field,
                { dismissed = true },
                { saved = it.quickActions.joinToString() },
            )
        }
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_pin_field)).performClick()
        composeRule.onNodeWithContentDescription(getResourceString(CommonString.save)).assertDoesNotExist()
        composeRule
            .onNodeWithContentDescription(
                getResourceString(androidx.appcompat.R.string.abc_action_bar_up_description),
            ).performClick()
        assertTrue(dismissed)
        composeRule.onNodeWithText(getResourceString(CommonString.discard)).assertDoesNotExist()
        assertEquals(
            field.toolbar.quickActions
                .filterNot { it == "pin" }
                .joinToString(),
            saved,
        )
    }

    @Test
    fun `equation text survives cancelling the discard confirmation`() {
        show {
            EditorFormattingDialog(
                EditorFormattingRequest(WebEditorAction.MATHJAX_INLINE, WebEditorTarget("test", 0, 0, 0, ""), "x"),
                { dismissed = true },
                { _, text -> saved = text },
            )
        }
        composeRule.onNode(hasSetTextAction()).performTextReplacement("x^2 + y^2")
        cancel()
        assertFalse(dismissed)
        keepEditing()
        composeRule.onNodeWithText(getResourceString(CommonString.dialog_ok)).performClick()
        assertEquals("x^2 + y^2", saved)
    }

    @Test
    fun `custom template edits can be kept without losing the parent library`() {
        val template = EditorCustomToolbarButton(label = "Ruby", prefix = "<ruby>", suffix = "</ruby>")
        show { EditorCustomButtonsDialog(listOf(template), { dismissed = true }, { saved = it.single().prefix }) }
        composeRule.onNodeWithText("Ruby").performClick()
        composeRule.onNodeWithText(getResourceString(CommonString.before_text)).performTextReplacement("<ruby class='reading'>")
        cancel()
        assertFalse(dismissed)
        keepEditing()
        composeRule.onNodeWithText(getResourceString(CommonString.save)).performClick()
        assertNull(saved)
        composeRule.onNodeWithText("Ruby").assertExists()
        composeRule.dismissEditorDialog()
        assertFalse(dismissed)
        keepEditing()
        composeRule.onNodeWithContentDescription(getResourceString(CommonString.save)).performClick()
        assertEquals("<ruby class='reading'>", saved)
    }

    private fun showDescription() =
        show {
            EditorFieldDescriptionDialog(field, { dismissed = true }, { saved = it })
        }

    private fun show(content: @Composable () -> Unit) {
        val activity = startActivityNormallyOpenCollectionWithIntent(AnkiActivity::class.java, Intent())
        activity.setContent { AnkiDroidTheme { content() } }
    }

    private fun cancel() = composeRule.onNodeWithText(getResourceString(CommonString.dialog_cancel)).performClick()

    private fun keepEditing() = composeRule.onNodeWithText(with(targetContext) { TR.sentenceCase.keepEditing }).performClick()

    private fun discard() = composeRule.onNodeWithText(getResourceString(CommonString.discard)).performClick()
}
