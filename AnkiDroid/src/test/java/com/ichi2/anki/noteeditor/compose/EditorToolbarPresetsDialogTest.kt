// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class EditorToolbarPresetsDialogTest : RobolectricTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()
    private val default = EditorToolbarPreset(id = EditorToolbarPreset.DEFAULT_ID, name = "")
    private val minimal = EditorToolbarPreset(name = "Minimal", hiddenButtons = setOf("BOLD"))
    private val presets = mutableStateOf(listOf(default, minimal))
    private var selection: String? = "not selected"
    private val toolbarVisible = mutableStateOf(true)

    @Test
    fun `field preset choices do not offer a private toolbar`() {
        showPresets(forField = true)
        composeRule.onNodeWithText("This field only").assertDoesNotExist()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_default_toolbar)).assertIsSelected()
    }

    @Test
    fun `choosing a preset assigns its identity without modifying either preset`() {
        showPresets(forField = true)
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_default_toolbar)).assertIsSelected()
        composeRule.onNodeWithText("Minimal").performClick()
        assertEquals(minimal.id, selection)
        assertEquals(listOf(default, minimal), presets.value)
    }

    @Test
    fun `hiding the toolbar disables preset selection but keeps its choice and shared editing`() {
        showPresets(forField = true)
        val visibility = composeRule.onNodeWithText(getResourceString(R.string.compose_editor_show_field_toolbar))
        visibility.performClick()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_default_toolbar)).assertIsNotEnabled().assertIsSelected()
        val other = composeRule.onNodeWithText("Minimal")
        other.assertIsNotEnabled().performClick()
        assertEquals("not selected", selection)
        composeRule
            .onNodeWithContentDescription(targetContext.getString(R.string.compose_editor_edit_preset, "Minimal"))
            .assertIsEnabled()
            .performClick()
        composeRule.onNodeWithContentDescription(getResourceString(CommonString.dialog_cancel)).performClick()
        visibility.performClick()
        other.assertIsEnabled().performClick()
        assertEquals(minimal.id, selection)
        assertEquals(listOf(default, minimal), presets.value)
    }

    @Test
    fun `shared editing saves explicitly and cancellation discards button changes`() {
        showPresets()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_default_toolbar)).performClick()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_bold)).performClick()
        composeRule.onNodeWithContentDescription(getResourceString(CommonString.dialog_cancel)).performClick()
        assertEquals(default, presets.value.first())
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_default_toolbar)).performClick()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_bold)).performClick()
        composeRule.onNodeWithContentDescription(getResourceString(CommonString.save)).performClick()
        assertTrue("BOLD" in presets.value.first().hiddenButtons)
        assertEquals(minimal, presets.value.last())
        assertEquals("not selected", selection)
    }

    @Test
    fun `adding validates names and cancelling the button editor does not create a preset`() {
        showPresets()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_add_preset)).performClick()
        val save = composeRule.onNodeWithText(getResourceString(CommonString.save))
        save.assertIsNotEnabled()
        val name = composeRule.onNodeWithText(getResourceString(R.string.compose_editor_preset_name))
        name.performTextReplacement(" minimal ")
        save.assertIsNotEnabled()
        name.performTextReplacement("Reading")
        save.performClick()
        assertEquals(2, presets.value.size)
        composeRule.onNodeWithContentDescription(getResourceString(CommonString.dialog_cancel)).performClick()
        assertEquals(2, presets.value.size)
    }

    @Test
    fun `duplicates keep the button choices but get an independent identity`() {
        showPresets()
        options("Minimal")
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_duplicate_preset)).performClick()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_preset_name)).performTextReplacement("Reading")
        composeRule.onNodeWithText(getResourceString(CommonString.save)).performClick()
        composeRule.onNodeWithContentDescription(getResourceString(CommonString.save)).performClick()
        val copy = presets.value.last()
        assertEquals("Reading", copy.name)
        assertEquals(minimal.hiddenButtons, copy.hiddenButtons)
        assertTrue(copy.id != minimal.id)
        assertEquals(minimal, presets.value[1])
    }

    @Test
    fun `rename retains identity and deletion requires confirmation`() {
        showPresets()
        options("Minimal")
        composeRule.onNodeWithText(getResourceString(CommonString.rename)).performClick()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_preset_name)).performTextReplacement("Reading")
        composeRule.onNodeWithText(getResourceString(CommonString.save)).performClick()
        assertEquals(minimal.id, presets.value.last().id)
        options("Reading")
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_delete_preset)).performClick()
        assertEquals(2, presets.value.size)
        composeRule.onNodeWithText(getResourceString(CommonString.dialog_cancel)).performClick()
        assertEquals(2, presets.value.size)
        options("Reading")
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_delete_preset)).performClick()
        composeRule.onNode(hasText(getResourceString(R.string.compose_editor_delete_preset)) and hasClickAction()).performClick()
        assertEquals(listOf(default), presets.value)
    }

    private fun options(name: String) {
        composeRule.onNodeWithContentDescription(targetContext.getString(R.string.compose_editor_preset_options, name)).performClick()
    }

    private fun showPresets(forField: Boolean = false) {
        val activity = startActivityNormallyOpenCollectionWithIntent(AnkiActivity::class.java, Intent())
        activity.setContent {
            AnkiDroidTheme {
                EditorToolbarPresetsDialog(
                    presets = presets.value,
                    templates = emptyList(),
                    onSavePreset = { updated ->
                        presets.value =
                            if (presets.value.any { it.id == updated.id }) {
                                presets.value.map {
                                    if (it.id ==
                                        updated.id
                                    ) {
                                        updated
                                    } else {
                                        it
                                    }
                                }
                            } else {
                                presets.value + updated
                            }
                    },
                    onDeletePreset = { id -> presets.value = presets.value.filterNot { it.id == id } },
                    onCustomButtons = {},
                    onDismiss = {},
                    field =
                        if (forField) {
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
                                toolbar = EditorFieldToolbar(showToolbar = toolbarVisible.value),
                            )
                        } else {
                            null
                        },
                    noteTypeName = "Basic",
                    onSelect = { selection = it },
                    onToolbarVisibility = { toolbarVisible.value = it },
                )
            }
        }
    }
}
