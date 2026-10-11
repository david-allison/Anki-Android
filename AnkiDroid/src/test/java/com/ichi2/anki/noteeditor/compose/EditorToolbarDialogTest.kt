// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.compose.theme.AnkiDroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class EditorToolbarDialogTest : RobolectricTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()
    private var saved: EditorFieldToolbar? = null

    @Test
    fun `custom buttons and hidden builtins are saved together only when confirmed`() {
        showDialog()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_bold)).performClick()
        composeRule.onNodeWithText(getResourceString(CommonString.add_toolbar_item)).performClick()
        val save = getResourceString(CommonString.save)
        composeRule.onNodeWithText(save).assertIsNotEnabled()
        composeRule.onNodeWithText(getResourceString(R.string.note_editor_toolbar_icon)).performTextInput("Ruby")
        composeRule.onNodeWithText(save).assertIsNotEnabled()
        composeRule.onNodeWithText(getResourceString(R.string.before_text)).performTextInput("<ruby>")
        composeRule.onNodeWithText(getResourceString(R.string.after_text)).performTextInput("</ruby>")
        composeRule.onNodeWithText(save).assertIsEnabled().performClick()
        assertNull(saved)
        composeRule.onNodeWithText(save).performClick()
        assertEquals(setOf("BOLD"), saved?.hiddenButtons)
        val button = requireNotNull(saved).customButtons.single()
        assertEquals("Ruby", button.label)
        assertEquals("<ruby>", button.prefix)
        assertEquals("</ruby>", button.suffix)
    }

    @Test
    fun `cancelling does not save removed custom buttons or changed visibility`() {
        val custom = EditorCustomToolbarButton(label = "Ruby", prefix = "<ruby>", suffix = "</ruby>")
        showDialog(EditorFieldToolbar(customButtons = listOf(custom)))
        composeRule
            .onNodeWithContentDescription(
                targetContext.getString(R.string.compose_editor_remove_custom_button, "Ruby"),
            ).performClick()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_bold)).performClick()
        composeRule.onNodeWithText(getResourceString(CommonString.dialog_cancel)).performClick()
        assertNull(saved)
    }

    private fun showDialog(toolbar: EditorFieldToolbar = EditorFieldToolbar()) {
        val activity = startActivityNormallyOpenCollectionWithIntent(AnkiActivity::class.java, Intent())
        activity.setContent {
            AnkiDroidTheme {
                EditorToolbarDialog(
                    target = EditorFieldTarget("dialog", 0, 1),
                    field = EditorFieldState("Back", "", "Arial", 20, false, null, false, false, false, toolbar),
                    noteTypeName = "Basic",
                    isCloze = false,
                    onDismiss = {},
                    onSave = { saved = it },
                )
            }
        }
    }
}
