// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
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
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class EditorToolbarDialogTest : RobolectricTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()
    private var saved: EditorFieldToolbar? = null

    private val ruby = EditorCustomToolbarButton(label = "Ruby", prefix = "<ruby>", suffix = "</ruby>")
    private var savedTemplates: List<EditorCustomToolbarButton>? = null
    private val shortToolbar =
        EditorFieldToolbar(
            hiddenButtons =
                EditorToolbarButton.entries.map { it.name }.toSet() - setOf("BOLD", "ITALIC"),
        )

    @Test
    fun `enabled toolbar rows precede available rows after toggling`() {
        showDialog(shortToolbar)
        val bold = getResourceString(R.string.compose_editor_bold)
        val italic = getResourceString(R.string.compose_editor_italic)
        assertAbove(bold, "Ruby")
        assertAbove(italic, "Ruby")
        composeRule.onNodeWithText(bold).performClick()
        assertAbove(italic, bold)
        composeRule.onNodeWithText("Ruby").performClick()
        assertAbove("Ruby", bold)
        composeRule.moveToolbarButton("Ruby", targetContext.getString(R.string.compose_editor_move_button_down, "Ruby"))
        assertAbove(italic, "Ruby")
        assertAbove("Ruby", bold)
        assertEquals(
            listOf(targetContext.getString(R.string.compose_editor_move_button_up, "Ruby")),
            reorderActions("Ruby").map { it.label },
        )
        composeRule.onNodeWithText(getResourceString(CommonString.save)).performClick()
        assertEquals(listOf(ruby), saved?.customButtons)
        assertTrue("BOLD" in requireNotNull(saved).hiddenButtons)
    }

    @Test
    fun `quick actions keep available rows below selected rows when toggled`() {
        showDialog(EditorFieldToolbar(quickActions = listOf("gallery", "pin")), quickActions = true)
        val gallery = getResourceString(CommonString.multimedia_editor_image_field_editing_galery)
        val pin = getResourceString(R.string.compose_editor_pin_field)
        val attach = getResourceString(CommonString.multimedia_editor_attach_tooltip)
        assertAbove(gallery, pin)
        composeRule.onNodeWithText(gallery).performClick()
        assertAbove(pin, gallery)
        composeRule.onNodeWithText(attach).performClick()
        assertAbove(attach, gallery)
        assertEquals(
            listOf(targetContext.getString(R.string.compose_editor_move_button_up, attach)),
            reorderActions(attach).map { it.label },
        )
        composeRule.moveToolbarButton(attach, targetContext.getString(R.string.compose_editor_move_button_up, attach))
        assertAbove(attach, pin)
        composeRule.onNodeWithText(getResourceString(CommonString.save)).performClick()
        assertEquals(listOf("attachMedia", "pin"), saved?.quickActions)
    }

    private fun assertAbove(
        first: String,
        second: String,
    ) {
        val firstBounds = composeRule.onNodeWithText(first).fetchSemanticsNode().boundsInWindow
        val secondBounds = composeRule.onNodeWithText(second).fetchSemanticsNode().boundsInWindow
        assertTrue(firstBounds.bottom <= secondBounds.top, "$first must appear above $second")
    }

    @Test
    fun `toolbar enables templates and hides builtins without editing definitions`() {
        showDialog(shortToolbar)
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_bold)).performClick()
        composeRule.onNodeWithText("Ruby").performClick()
        composeRule.onNodeWithText(getResourceString(CommonString.save)).performClick()
        assertEquals(shortToolbar.hiddenButtons + "BOLD", saved?.hiddenButtons)
        assertEquals(listOf(ruby), saved?.customButtons)
        assertNull(savedTemplates)
    }

    @Test
    fun `reordering mixes templates and Anki buttons and keeps disabled positions`() {
        showDialog(shortToolbar.copy(customButtons = listOf(ruby)))
        val up = targetContext.getString(R.string.compose_editor_move_button_up, "Ruby")
        val down = targetContext.getString(R.string.compose_editor_move_button_down, "Ruby")
        assertEquals(listOf(down), reorderActions("Ruby").map { it.label })
        composeRule.moveToolbarButton("Ruby", down)
        assertEquals(listOf(up, down), reorderActions("Ruby").map { it.label })
        composeRule.moveToolbarButton("Ruby", down)
        composeRule.onNodeWithText("Ruby").performClick()
        composeRule.onNodeWithText("Ruby").performClick()
        composeRule.onNodeWithText(getResourceString(CommonString.save)).performClick()
        assertEquals(listOf("BOLD", "ITALIC", "custom:${ruby.id}"), saved?.buttonOrder?.take(3))
        assertEquals(listOf(ruby), saved?.customButtons)
    }

    @Test
    fun `cancelling does not save changed visibility`() {
        showDialog(EditorFieldToolbar(customButtons = listOf(ruby)))
        composeRule.moveToolbarButton("Ruby", targetContext.getString(R.string.compose_editor_move_button_down, "Ruby"))
        composeRule.onNodeWithText("Ruby").performClick()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_bold)).performClick()
        composeRule.onNodeWithContentDescription(getResourceString(CommonString.dialog_cancel)).performClick()
        assertNull(saved)
    }

    @Test
    fun `library validates new templates and saves only when confirmed`() {
        showLibrary()
        composeRule.onNodeWithText(getResourceString(CommonString.add_toolbar_item)).performClick()
        val save = getResourceString(CommonString.save)
        composeRule.onNodeWithText(save).assertIsNotEnabled()
        composeRule.onNodeWithText(getResourceString(CommonString.note_editor_toolbar_icon)).performTextInput("Ruby")
        composeRule.onNodeWithText(save).assertIsNotEnabled()
        composeRule.onNodeWithText(getResourceString(CommonString.before_text)).performTextInput("<ruby>")
        composeRule.onNodeWithText(getResourceString(CommonString.after_text)).performTextInput("</ruby>")
        composeRule.onNodeWithText(save).assertIsEnabled().performClick()
        assertNull(savedTemplates)
        composeRule.onNodeWithText(save).performClick()
        val button = requireNotNull(savedTemplates).single()
        assertEquals("Ruby", button.label)
        assertEquals("<ruby>", button.prefix)
        assertEquals("</ruby>", button.suffix)
    }

    @Test
    fun `library edits retain identity and cancelled deletions are discarded`() {
        showLibrary(listOf(ruby))
        composeRule.onNodeWithText("Ruby").performClick()
        composeRule.onNodeWithText(getResourceString(CommonString.note_editor_toolbar_icon)).performTextReplacement("Ruby2")
        composeRule.onNodeWithText(getResourceString(CommonString.save)).performClick()
        composeRule.onNodeWithText(getResourceString(CommonString.save)).performClick()
        assertEquals(ruby.id, savedTemplates?.single()?.id)
        val changed = requireNotNull(savedTemplates).single()
        assertEquals("Ruby2", changed.label)
        savedTemplates = null
        composeRule
            .onNodeWithContentDescription(
                targetContext.getString(R.string.compose_editor_remove_custom_button, changed.label),
            ).performClick()
        composeRule.onNodeWithText(getResourceString(CommonString.dialog_cancel)).performClick()
        assertNull(savedTemplates)
    }

    private fun reorderActions(label: String) =
        composeRule
            .onNodeWithContentDescription(
                targetContext.getString(R.string.compose_editor_reorder_button, label),
            ).fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]

    private fun showLibrary(templates: List<EditorCustomToolbarButton> = emptyList()) {
        val activity = startActivityNormallyOpenCollectionWithIntent(AnkiActivity::class.java, Intent())
        activity.setContent {
            AnkiDroidTheme {
                EditorCustomButtonsDialog(templates, onDismiss = {}, onSave = { savedTemplates = it })
            }
        }
    }

    private fun showDialog(
        toolbar: EditorFieldToolbar = EditorFieldToolbar(),
        quickActions: Boolean = false,
    ) {
        val activity = startActivityNormallyOpenCollectionWithIntent(AnkiActivity::class.java, Intent())
        activity.setContent {
            AnkiDroidTheme {
                if (quickActions) {
                    EditorQuickActionsDialog(
                        target = EditorFieldTarget("dialog", 0, 1),
                        field = EditorFieldState("Back", "", "Arial", 20, false, null, false, false, false, toolbar),
                        noteTypeName = "Basic",
                        onDismiss = {},
                        onSave = { saved = it },
                    )
                } else {
                    EditorToolbarDialog(
                        target = EditorFieldTarget("dialog", 0, 1),
                        field = EditorFieldState("Back", "", "Arial", 20, false, null, false, false, false, toolbar),
                        noteTypeName = "Basic",
                        isCloze = false,
                        templates = listOf(ruby),
                        onDismiss = {},
                        onSave = { saved = it },
                    )
                }
            }
        }
    }
}
