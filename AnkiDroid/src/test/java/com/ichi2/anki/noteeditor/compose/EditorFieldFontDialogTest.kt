// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsNotEnabled
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
import com.ichi2.compose.theme.AnkiDroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class EditorFieldFontDialogTest : RobolectricTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()
    private var saved: Pair<String, Int>? = null
    private var dismissed = false

    @Test
    fun `changing size preserves the existing font name`() {
        showFont(fontName = "Custom Imported Font")
        composeRule.onNodeWithText(TR.fieldsFont()).assertDoesNotExist()
        composeRule.onNodeWithText("Custom Imported Font").assertDoesNotExist()
        val size = composeRule.onNode(hasSetTextAction())
        val save = composeRule.onNodeWithText(getResourceString(CommonString.save))
        size.performTextReplacement("")
        save.assertIsNotEnabled()
        size.performTextReplacement("301")
        save.assertIsNotEnabled()
        size.performTextReplacement("4")
        save.assertIsNotEnabled()
        size.performTextReplacement("27")
        save.performClick()
        assertEquals("Custom Imported Font" to 27, saved)
    }

    @Test
    fun `cancelling discards typography edits`() {
        showFont()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("35")
        composeRule.onNodeWithText(getResourceString(CommonString.dialog_cancel)).performClick()
        assertFalse(dismissed)
        composeRule.onNodeWithText(getResourceString(CommonString.discard)).performClick()
        assertTrue(dismissed)
        assertNull(saved)
    }

    @Test
    fun `main options no longer offer a global text size`() {
        val activity = startActivityNormallyOpenCollectionWithIntent(AnkiActivity::class.java, Intent())
        activity.setContent {
            AnkiDroidTheme {
                EditorOptionsMenu(
                    enabled = true,
                    onFields = {},
                    onCards = {},
                    toolbarDisplay = EditorToolbarDisplay.SCROLL,
                    onToolbarDisplay = {},
                    fieldTarget = EditorFieldTarget("test", 1, 0),
                    onFieldOptions = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_options)).performClick()
        composeRule.onNodeWithText("Editor text size").assertDoesNotExist()
    }

    private fun showFont(fontName: String = "Arial") {
        val activity = startActivityNormallyOpenCollectionWithIntent(AnkiActivity::class.java, Intent())
        activity.setContent {
            AnkiDroidTheme {
                EditorFieldFontDialog(
                    field = EditorFieldState("Front", "", fontName, 20, false, null, false, false, false),
                    onDismiss = { dismissed = true },
                    onSave = { font, size -> saved = font to size },
                )
            }
        }
    }
}
