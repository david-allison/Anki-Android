// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.RobolectricTest
import com.ichi2.compose.theme.AnkiDroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class EditorToolbarTooltipTest : RobolectricTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()
    private val toolbar = mutableStateOf(EditorFieldToolbar())
    private val state = mutableStateOf(EditorToolbarState(hasSelection = true))
    private var actions = 0

    @Test
    fun `undo and redo are disabled for an untouched field`() {
        toolbar.value = EditorFieldToolbar(hiddenButtons = EditorToolbarButton.entries.map { it.name }.toSet() - setOf("UNDO", "REDO"))
        showButtons()
        composeRule.onNodeWithContentDescription(getResourceString(EditorToolbarButton.UNDO.label)).assertIsNotEnabled()
        composeRule.onNodeWithContentDescription(getResourceString(EditorToolbarButton.REDO.label)).assertIsNotEnabled()
    }

    @Test
    fun `undo and redo follow the active fields history in either editing mode`() {
        toolbar.value = EditorFieldToolbar(hiddenButtons = EditorToolbarButton.entries.map { it.name }.toSet() - setOf("UNDO", "REDO"))
        showButtons()
        val undo = composeRule.onNodeWithContentDescription(getResourceString(EditorToolbarButton.UNDO.label))
        val redo = composeRule.onNodeWithContentDescription(getResourceString(EditorToolbarButton.REDO.label))
        for (sourceMode in listOf(false, true)) {
            composeRule.runOnIdle { state.value = EditorToolbarState(hasSelection = true, sourceMode = sourceMode, canUndo = true) }
            undo.assertIsEnabled().performClick()
            redo.assertIsNotEnabled()
            composeRule.runOnIdle { state.value = state.value.copy(canUndo = false, canRedo = true) }
            undo.assertIsNotEnabled()
            redo.assertIsEnabled().performClick()
            composeRule.runOnIdle { state.value = state.value.copy(activeField = 1, canRedo = false) }
            undo.assertIsNotEnabled()
            redo.assertIsNotEnabled()
        }
        assertEquals(4, actions)
    }

    @Test
    fun `available history stays disabled during composition or without a selection`() {
        toolbar.value = EditorFieldToolbar(hiddenButtons = EditorToolbarButton.entries.map { it.name }.toSet() - setOf("UNDO", "REDO"))
        state.value = EditorToolbarState(hasSelection = true, composing = true, canUndo = true, canRedo = true)
        showButtons()
        val undo = composeRule.onNodeWithContentDescription(getResourceString(EditorToolbarButton.UNDO.label))
        val redo = composeRule.onNodeWithContentDescription(getResourceString(EditorToolbarButton.REDO.label))
        undo.assertIsNotEnabled()
        redo.assertIsNotEnabled()
        composeRule.runOnIdle { state.value = state.value.copy(composing = false, hasSelection = false) }
        undo.assertIsNotEnabled()
        redo.assertIsNotEnabled()
    }

    @Test
    fun `mode switch follows the active field and describes the next mode`() {
        toolbar.value = EditorFieldToolbar(buttonOrder = listOf("SOURCE_MODE"))
        showButtons()
        val html = getResourceString(EditorToolbarButton.SOURCE_MODE.label)
        composeRule.onNodeWithContentDescription(html).assertIsOn().performClick()
        assertEquals(1, actions)
        composeRule.runOnIdle { state.value = state.value.copy(sourceMode = true) }
        val richText = composeRule.onNodeWithContentDescription("Edit rich text")
        richText.assertIsOff().performTouchInput { longClick() }
        composeRule.onNodeWithText("Edit rich text").assertIsDisplayed()
        assertEquals(1, actions, "Long press must only explain the action")
        richText.performClick()
        assertEquals(2, actions)
        composeRule.runOnIdle { state.value = state.value.copy(activeField = 1, sourceMode = false) }
        composeRule.onNodeWithContentDescription(html).assertIsOn()
    }

    @Test
    fun `every Anki button shows its label without executing its action`() {
        state.value = state.value.copy(canUndo = true, canRedo = true)
        showButtons()
        for (button in EditorToolbarButton.entries) {
            composeRule.runOnIdle {
                toolbar.value =
                    EditorFieldToolbar(
                        hiddenButtons =
                            EditorToolbarButton.entries
                                .filterNot { it == button }
                                .map { it.name }
                                .toSet(),
                    )
                actions = 0
            }
            val label = getResourceString(button.label)
            val icon = composeRule.onNodeWithContentDescription(label)
            icon.performTouchInput { longClick() }
            composeRule.onNodeWithText(label).assertIsDisplayed()
            assertEquals(0, actions)
            icon.performClick()
            assertEquals(1, actions)
        }
    }

    @Test
    fun `disabled buttons still explain their action on long press`() {
        showButtons(enabled = false)
        val label = getResourceString(EditorToolbarButton.BOLD.label)
        composeRule.onNodeWithContentDescription(label).performTouchInput { longClick() }
        composeRule.onNodeWithText(label).assertIsDisplayed()
        assertEquals(0, actions)
    }

    @Test
    fun `custom button tooltip shows its complete label without inserting text`() {
        val label = "Wrap the selection in ruby annotation with pronunciation"
        toolbar.value =
            EditorFieldToolbar(customButtons = listOf(EditorCustomToolbarButton(label = label, prefix = "<ruby>", suffix = "</ruby>")))
        showButtons()
        val button = composeRule.onNodeWithText(label)
        button.performTouchInput { longClick() }
        composeRule.onNode(hasText(label) and hasAnyAncestor(isPopup())).assertIsDisplayed()
        assertEquals(0, actions)
        composeRule.onNode(hasText(label) and !hasAnyAncestor(isPopup())).performClick()
        assertEquals(1, actions)
    }

    private fun showButtons(enabled: Boolean = true) {
        val activity = startActivityNormallyOpenCollectionWithIntent(AnkiActivity::class.java, Intent())
        activity.setContent {
            AnkiDroidTheme {
                key(toolbar.value) {
                    Row {
                        EditorFormattingButtons(
                            toolbar = toolbar.value,
                            state = state.value,
                            enabled = enabled,
                            isCloze = true,
                            onAction = { actions++ },
                            onCustomAction = { actions++ },
                            onMedia = { actions++ },
                            onRecordAudio = { actions++ },
                        )
                    }
                }
            }
        }
    }
}
