// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
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
    private var actions = 0

    @Test
    fun `every Anki button shows its label without executing its action`() {
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
                            state = EditorToolbarState(hasSelection = true),
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
