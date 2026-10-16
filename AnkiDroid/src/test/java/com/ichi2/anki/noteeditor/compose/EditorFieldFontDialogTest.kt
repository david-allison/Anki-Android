// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsNotEnabled
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
import com.ichi2.anki.noteeditor.web.WebEditorSettings
import com.ichi2.compose.theme.AnkiDroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class EditorFieldFontDialogTest : RobolectricTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()
    private var saved: Pair<String, Int>? = null
    private var dismissed = false

    @Test
    fun `font names and size are validated and saved together`() {
        showFont()
        val font = composeRule.onNodeWithText(TR.fieldsFont())
        val size = composeRule.onNodeWithText(getResourceString(CommonString.menu_font_size))
        val save = composeRule.onNodeWithText(getResourceString(CommonString.save))
        font.performTextReplacement("")
        save.assertIsNotEnabled()
        font.performTextReplacement(" Custom Imported Font ")
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
        composeRule.onNodeWithText(getResourceString(CommonString.menu_font_size)).performTextReplacement("35")
        composeRule.onNodeWithText(getResourceString(CommonString.dialog_cancel)).performClick()
        assertTrue(dismissed)
        assertNull(saved)
    }

    @Test
    fun `use field sizes removes the device override`() {
        var settings = WebEditorSettings(fontSize = 30)
        val activity = startActivityNormallyOpenCollectionWithIntent(AnkiActivity::class.java, Intent())
        activity.setContent {
            AnkiDroidTheme {
                EditorOptionsMenu(
                    settings = settings,
                    enabled = true,
                    onSettings = { settings = it },
                    onFields = {},
                    onCards = {},
                    toolbarDisplay = EditorToolbarDisplay.SCROLL,
                    onToolbarDisplay = {},
                    onCustomButtons = {},
                    fieldOptionsEnabled = true,
                    onFieldOptions = {},
                    onToolbarPresets = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_options)).performClick()
        composeRule.onNodeWithText(getResourceString(CommonString.menu_font_size)).performClick()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_use_field_sizes)).performClick()
        assertNull(settings.fontSize)
    }

    private fun showFont() {
        val activity = startActivityNormallyOpenCollectionWithIntent(AnkiActivity::class.java, Intent())
        activity.setContent {
            AnkiDroidTheme {
                EditorFieldFontDialog(
                    field = EditorFieldState("Front", "", "Arial", 20, false, null, false, false, false),
                    noteTypeName = "Basic",
                    fontSizeOverride = null,
                    onDismiss = { dismissed = true },
                    onSave = { font, size -> saved = font to size },
                )
            }
        }
    }
}
