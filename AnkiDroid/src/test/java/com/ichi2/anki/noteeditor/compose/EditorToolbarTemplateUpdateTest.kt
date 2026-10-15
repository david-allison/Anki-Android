// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class EditorToolbarTemplateUpdateTest : RobolectricTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun `shared template changes preserve pending field choices`() {
        val ruby = EditorCustomToolbarButton(label = "Ruby", prefix = "<ruby>", suffix = "</ruby>")
        val templates = mutableStateOf(listOf(ruby))
        val initial =
            EditorFieldToolbar(
                customButtons = listOf(ruby),
                hiddenButtons = EditorToolbarButton.entries.map { it.name }.toSet() - setOf("BOLD", "ITALIC"),
            )
        var saved: EditorFieldToolbar? = null
        val activity = startActivityNormallyOpenCollectionWithIntent(AnkiActivity::class.java, Intent())
        activity.setContent {
            AnkiDroidTheme {
                EditorToolbarDialog(
                    target = EditorFieldTarget("templates", 0, 1),
                    field = EditorFieldState("Back", "", "Arial", 20, false, null, false, false, false, initial),
                    isCloze = false,
                    templates = templates.value,
                    onCustomButtons = {},
                    onDismiss = {},
                    onSave = { saved = it },
                )
            }
        }
        val bold = composeRule.onNodeWithText(getResourceString(R.string.compose_editor_bold))
        bold.performClick().assertIsNotSelected()
        val renamed = ruby.copy(label = "Ruby 2", prefix = "<ruby class='reading'>")
        composeRule.runOnIdle { templates.value = listOf(renamed) }
        composeRule.onNodeWithText("Ruby 2").assertIsSelected()
        bold.assertIsNotSelected()
        assertNull(saved, "Updating shared templates must not save the field")
        val save = composeRule.onNodeWithContentDescription(getResourceString(CommonString.save))
        save.performClick()
        assertEquals(listOf(renamed), assertNotNull(saved).customButtons)
        assertTrue("BOLD" in assertNotNull(saved).hiddenButtons)

        composeRule.runOnIdle { templates.value = emptyList() }
        composeRule.onNodeWithText("Ruby 2").assertDoesNotExist()
        save.performClick()
        assertTrue(assertNotNull(saved).customButtons.isEmpty(), "Saving must not resurrect deleted templates")
        assertTrue("BOLD" in assertNotNull(saved).hiddenButtons)
    }
}
