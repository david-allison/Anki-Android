// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w320dp-h900dp-mdpi")
class EditorToolbarPresetsDialogTest : RobolectricTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()
    private val default =
        EditorToolbarPreset(
            id = EditorToolbarPreset.DEFAULT_ID,
            name = "",
            hiddenButtons = EditorToolbarButton.entries.map { it.name }.toSet() - setOf("BOLD", "ITALIC"),
        )
    private val minimal = default.copy(id = "minimal", name = "Minimal", hiddenButtons = default.hiddenButtons + "BOLD")
    private val presets = listOf(default, minimal)
    private var saved: EditorToolbarConfiguration? = null
    private var dismissed = false

    @Test
    fun `customizing a toolbar opens its buttons without a second edit step`() {
        showPresets()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_bold)).assertIsSelected()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_preset_scope)).assertExists()
        composeRule.onNodeWithText("This field only").assertDoesNotExist()
    }

    @Test
    fun `choosing a preset assigns its identity without modifying either preset`() {
        showPresets()
        choose("Minimal")
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_italic)).assertIsSelected()
        assertNull(saved)
        save()
        assertEquals(EditorToolbarConfiguration(presets, minimal.id, true), saved)
    }

    @Test
    fun `switching presets preserves pending edits and saves them together`() {
        showPresets()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_bold)).performClick()
        choose("Minimal")
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_italic)).performClick()
        choose(getResourceString(R.string.compose_editor_default_toolbar))
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_italic)).assertIsSelected()
        assertNull(saved)
        save()
        val result = assertNotNull(saved)
        assertTrue("BOLD" in result.presets.first().hiddenButtons)
        assertTrue("ITALIC" in result.presets.last().hiddenButtons)
        assertEquals(default.id, result.presetId)
    }

    @Test
    fun `hiding the toolbar disables customization while allowing save and cancellation`() {
        showPresets()
        choose("Minimal")
        val visibility = composeRule.onNodeWithText(getResourceString(R.string.compose_editor_show_field_toolbar))
        visibility.performClick()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_preset)).assertIsNotEnabled()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_italic)).assertIsNotEnabled()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_custom_buttons)).assertIsNotEnabled()
        composeRule
            .onNodeWithContentDescription(targetContext.getString(R.string.compose_editor_preset_options, "Minimal"))
            .assertIsNotEnabled()
        visibility.assertIsEnabled()
        composeRule.onNodeWithContentDescription(getResourceString(CommonString.save)).assertIsEnabled()
        composeRule.dismissEditorDialog()
        composeRule.onNodeWithText(getResourceString(CommonString.discard)).assertExists()
        composeRule.dismissEditorDialog()
        assertNull(saved)
        save()
        assertEquals(EditorToolbarConfiguration(presets, minimal.id, false), saved)
    }

    @Test
    fun `showing the toolbar again restores button editing and accessible reordering`() {
        showPresets()
        val visibility = composeRule.onNodeWithText(getResourceString(R.string.compose_editor_show_field_toolbar))
        val bold = getResourceString(R.string.compose_editor_bold)
        val drag = composeRule.onNodeWithContentDescription(targetContext.getString(R.string.compose_editor_reorder_button, bold))
        assertTrue(drag.fetchSemanticsNode().config[SemanticsActions.CustomActions].isNotEmpty())
        visibility.performClick()
        drag.assertIsNotEnabled()
        assertTrue(drag.fetchSemanticsNode().config[SemanticsActions.CustomActions].isEmpty())
        visibility.performClick()
        drag.assertIsEnabled()
        assertTrue(drag.fetchSemanticsNode().config[SemanticsActions.CustomActions].isNotEmpty())
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_preset)).assertIsEnabled()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_custom_buttons)).assertIsEnabled()
        composeRule.onNodeWithText(bold).assertIsEnabled().performClick()
        save()
        assertTrue("BOLD" in assertNotNull(saved).presets.first().hiddenButtons)
    }

    @Test
    fun `cancellation discards pending visibility choice and shared button changes`() {
        showPresets()
        choose("Minimal")
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_italic)).performClick()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_show_field_toolbar)).performClick()
        composeRule.dismissEditorDialog()
        assertFalse(dismissed)
        composeRule.onNodeWithText(getResourceString(CommonString.discard)).performClick()
        assertTrue(dismissed)
        assertNull(saved)
    }

    @Test
    fun `unfinished preset names require their own discard confirmation`() {
        showPresets()
        options(getResourceString(R.string.compose_editor_default_toolbar))
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_add_preset)).performClick()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_preset_name)).performTextReplacement("Reading")
        composeRule.onNodeWithText(getResourceString(CommonString.dialog_cancel)).performClick()
        assertFalse(dismissed)
        composeRule.onNodeWithText(getResourceString(CommonString.discard)).performClick()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_preset_name)).assertDoesNotExist()
        assertNull(saved)
        // Discarding the child leaves the unchanged parent free to close immediately.
        composeRule.dismissEditorDialog()
        assertTrue(dismissed)
    }

    @Test
    fun `new preset names receive focus when the dialog opens`() {
        showPresets()
        options(getResourceString(R.string.compose_editor_default_toolbar))
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_add_preset)).performClick()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_preset_name)).assertIsFocused()
    }

    @Test
    fun `blank new preset names are neutral until a duplicate is entered`() {
        showPresets()
        options(getResourceString(R.string.compose_editor_default_toolbar))
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_add_preset)).performClick()
        val name = composeRule.onNodeWithText(getResourceString(R.string.compose_editor_preset_name))
        val error = composeRule.onNodeWithText(getResourceString(R.string.compose_editor_preset_name_error))
        val ok = composeRule.onNodeWithText(getResourceString(CommonString.dialog_ok))
        name.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
        error.assertDoesNotExist()
        ok.assertIsNotEnabled()
        name.performTextReplacement("   ")
        name.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
        error.assertDoesNotExist()
        ok.assertIsNotEnabled()
        name.performTextReplacement(" minimal ")
        name.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
        error.assertExists()
        ok.assertIsNotEnabled()
        name.performTextReplacement("")
        name.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
        error.assertDoesNotExist()
        ok.assertIsNotEnabled()
    }

    @Test
    fun `adding validates names and cancellation does not create a preset`() {
        showPresets()
        options(getResourceString(R.string.compose_editor_default_toolbar))
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_add_preset)).performClick()
        val ok = composeRule.onNodeWithText(getResourceString(CommonString.dialog_ok))
        ok.assertIsNotEnabled()
        val name = composeRule.onNodeWithText(getResourceString(R.string.compose_editor_preset_name))
        name.performTextReplacement(" minimal ")
        ok.assertIsNotEnabled()
        name.performTextReplacement(" default ")
        ok.assertIsNotEnabled()
        name.performTextReplacement("Reading")
        ok.performClick()
        composeRule.onNodeWithText("Reading").assertExists()
        assertNull(saved)
        composeRule.dismissEditorDialog()
        assertNull(saved)
    }

    @Test
    fun `duplicating selects an independent copy without changing its source`() {
        showPresets()
        choose("Minimal")
        options("Minimal")
        composeRule.onNodeWithText(getResourceString(com.ichi2.anki.ankicommon.R.string.sentence_clone_preset)).performClick()
        namePreset("Reading")
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_italic)).performClick()
        save()
        val result = assertNotNull(saved)
        val copy = result.presets.last()
        assertEquals("Reading", copy.name)
        assertEquals(minimal.hiddenButtons + "ITALIC", copy.hiddenButtons)
        assertNotEquals(minimal.id, copy.id)
        assertEquals(copy.id, result.presetId)
        assertEquals(presets, result.presets.take(2))
    }

    @Test
    fun `renaming preserves identity and pending button edits`() {
        showPresets()
        choose("Minimal")
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_italic)).performClick()
        options("Minimal")
        composeRule.onNodeWithText(getResourceString(CommonString.rename)).performClick()
        namePreset("Reading")
        assertNull(saved)
        save()
        val result = assertNotNull(saved)
        assertEquals(minimal.id, result.presetId)
        assertEquals("Reading", result.presets.last().name)
        assertTrue("ITALIC" in result.presets.last().hiddenButtons)
    }

    @Test
    fun `deleting requires confirmation and selects Default without saving early`() {
        showPresets()
        choose("Minimal")
        options("Minimal")
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_delete_preset)).performClick()
        composeRule.onNodeWithText(getResourceString(CommonString.dialog_cancel)).performClick()
        composeRule.onNodeWithText("Minimal").assertExists()
        options("Minimal")
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_delete_preset)).performClick()
        composeRule.onNode(hasText(getResourceString(R.string.compose_editor_delete_preset)) and hasClickAction()).performClick()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_default_toolbar)).assertExists()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_bold)).assertIsSelected()
        assertNull(saved)
        save()
        assertEquals(EditorToolbarConfiguration(listOf(default), default.id, true), saved)
    }

    private fun openChoices() = composeRule.onNodeWithText(getResourceString(R.string.compose_editor_preset)).performClick()

    private fun choose(name: String) {
        openChoices()
        composeRule.onNode(hasText(name) and hasAnyAncestor(isPopup())).performClick()
    }

    private fun options(name: String) {
        composeRule.onNodeWithContentDescription(targetContext.getString(R.string.compose_editor_preset_options, name)).performClick()
    }

    private fun namePreset(name: String) {
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_preset_name)).performTextReplacement(name)
        composeRule.onNodeWithText(getResourceString(CommonString.dialog_ok)).performClick()
    }

    private fun save() = composeRule.onNodeWithContentDescription(getResourceString(CommonString.save)).performClick()

    private fun showPresets() {
        val activity = startActivityNormallyOpenCollectionWithIntent(AnkiActivity::class.java, Intent())
        activity.setContent {
            AnkiDroidTheme {
                EditorCustomizeToolbarDialog(
                    presets = presets,
                    templates = emptyList(),
                    onSave = { saved = it },
                    onCustomButtons = {},
                    onDismiss = { dismissed = true },
                    field =
                        EditorFieldState(
                            "Front",
                            "",
                            "Arial",
                            20,
                            false,
                            null,
                            false,
                            false,
                            false,
                            toolbar = default.applyTo(EditorFieldToolbar(), emptyList()),
                        ),
                    noteTypeName = "Basic",
                )
            }
        }
    }
}
