// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R
import com.ichi2.anki.ScreenshotTest
import com.ichi2.compose.theme.AnkiDroidTheme
import com.ichi2.testutils.dispatchInsets
import com.ichi2.testutils.simulateKeyboard
import com.ichi2.utils.dp
import org.junit.Rule
import org.junit.Test

/**
 * Native field options above the keyboard; Robolectric does not render the web fields.
 *
 * `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests "*ComposeNoteEditorScreenshotTest"`
 */
class ComposeNoteEditorScreenshotTest : ScreenshotTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun optionsClosedAfterKeyboardDismissalAndBack() {
        val (activity, keyboard) = openOptions()
        composeRule.runOnIdle {
            (keyboard.parent as ViewGroup).removeView(keyboard)
            activity.dispatchInsets(navBarBottom = 48.dp)
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_bold)).assertIsDisplayed()
        captureScreen("options_closed_after_keyboard_back")

        val title = targetContext.getString(R.string.compose_editor_field_options_title, "Back")
        val scope = targetContext.getString(R.string.compose_editor_field_options_scope, "Basic")
        composeRule.onNodeWithContentDescription("$title. $scope").assertIsNotDisplayed()
    }

    @Test
    fun optionsOpen() {
        openOptions()
        captureScreen("options_open")
    }

    @Test
    fun helpOpen() {
        openOptions()
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_field_options_help)).performClick()
        composeRule
            .onNodeWithText(
                targetContext.getString(R.string.compose_editor_field_options_help_scope, "Back", "Basic"),
            ).assertIsDisplayed()
        captureScreen("help_open")
    }

    @Test
    fun pinLongPress() {
        captureLongPress(R.string.compose_editor_pin_field, R.string.compose_editor_pin_field_summary, "pin_long_press")
    }

    @Test
    fun keyboardLongPress() {
        captureLongPress(R.string.compose_editor_language_hint, R.string.compose_editor_language_hint_summary, "keyboard_long_press")
    }

    private fun captureLongPress(
        label: Int,
        explanation: Int,
        name: String,
    ) {
        openOptions()
        composeRule.onNodeWithContentDescription(getResourceString(label)).performTouchInput { longClick() }
        composeRule.onNodeWithText(getResourceString(explanation)).assertIsDisplayed()
        captureScreen(name)
    }

    private fun openOptions(
        fieldName: String = "Back",
        noteTypeName: String = "Basic",
    ): Pair<AnkiActivity, View> {
        val editor = showEditor(fieldName = fieldName, noteTypeName = noteTypeName)
        // The hidden sheet also composes its collapse button; choose the one beside the formatting controls.
        val bold = hasContentDescription(getResourceString(R.string.compose_editor_bold))
        composeRule
            .onNode(
                hasContentDescription(getResourceString(R.string.compose_editor_field_options)) and
                    hasAnySibling(bold or hasAnyDescendant(bold)),
            ).performClick()
        composeRule.onNodeWithText("Japanese").assertIsDisplayed()
        composeRule.waitForIdle()
        return editor
    }

    private fun showEditor(
        fieldName: String = "Back",
        noteTypeName: String = "Basic",
        deckName: String = "Default",
    ): Pair<AnkiActivity, View> {
        val activity = startActivityNormallyOpenCollectionWithIntent(AnkiActivity::class.java, Intent())
        activity.setContent {
            AnkiDroidTheme {
                NoteEditorScreen(
                    state = editorState(fieldName, noteTypeName, deckName),
                    toolbarState = remember { mutableStateOf(EditorToolbarState(hasSelection = true, activeField = 1)) },
                    ready = true,
                    busy = false,
                    error = null,
                    snackbar = remember { SnackbarHostState() },
                    onBack = {},
                    onSave = {},
                    loadNoteTypes = { emptyList() },
                    onDeck = {},
                    onType = {},
                    onEditType = {},
                    onTags = {},
                    onPinned = { _, _ -> },
                    onLanguageHint = {},
                    onAction = {},
                    onMedia = {},
                    onLegacy = {},
                    onPreviewVisibility = {},
                    editor = { Box(it) },
                    preview = { Box(it) },
                )
            }
        }
        composeRule.waitForIdle()
        val keyboard = activity.simulateKeyboard()
        composeRule.waitForIdle()
        return activity to keyboard
    }

    private fun editorState(
        fieldName: String,
        noteTypeName: String,
        deckName: String,
    ) = EditorState(
        sessionId = "screenshot",
        generation = 0,
        isAdding = true,
        noteId = 0,
        cardId = null,
        deckId = 1,
        deckName = deckName,
        noteTypeId = 1,
        noteTypeName = noteTypeName,
        notetypeJson = "{}",
        mediaDirectory = "",
        isCloze = false,
        fields =
            listOf("Front", fieldName).mapIndexed { index, name ->
                EditorFieldState(
                    name = name,
                    html = "",
                    font = "Arial",
                    fontSize = 20,
                    rtl = false,
                    languageTag = if (index == 1) "ja" else null,
                    sticky = index == 1,
                    collapsed = false,
                    sourceMode = false,
                )
            },
        tags = emptyList(),
    )

    private companion object {
        const val LONG_FIELD_NAME = "Japanese reading, pronunciation, pitch accent and detailed usage notes"
        const val LONG_NOTE_TYPE_NAME =
            "Japanese vocabulary with readings, pitch accent, audio, example sentences and detailed grammar explanations"
        const val LONG_DECK_NAME =
            "Languages::Japanese::Advanced vocabulary::Reading practice::Words to revisit before the next proficiency exam"
    }
}
