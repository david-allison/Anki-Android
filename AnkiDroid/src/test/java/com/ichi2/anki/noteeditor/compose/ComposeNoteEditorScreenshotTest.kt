// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
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
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.noteeditor.web.WebEditorAction
import com.ichi2.compose.theme.AnkiDroidTheme
import com.ichi2.testutils.dispatchInsets
import com.ichi2.testutils.simulateKeyboard
import com.ichi2.utils.dp
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Native field options above the keyboard; Robolectric does not render the web fields.
 *
 * `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests "*ComposeNoteEditorScreenshotTest"`
 */
class ComposeNoteEditorScreenshotTest : ScreenshotTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun snackbarAppearsAboveTags() {
        val snackbar = SnackbarHostState()
        val (activity, keyboard) = showEditor(tags = listOf("Vocabulary"), snackbarHost = snackbar)
        hideKeyboard(activity, keyboard)
        val message = targetContext.getString(R.string.compose_editor_tag_removed, "Japanese")
        composeRule.runOnIdle {
            activity.lifecycleScope.launch {
                snackbar.showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Indefinite)
            }
        }
        composeRule.waitForIdle()
        val feedback = activity.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        assertNotNull(feedback, "The editor must use the standard snackbar wrapper")
        assertEquals(message, feedback.text.toString())
        val location = IntArray(2)
        feedback.getLocationInWindow(location)
        val tag = composeRule.onNodeWithText("Vocabulary").fetchSemanticsNode().boundsInWindow
        assertTrue(location[1] + feedback.height < tag.top, "Removal feedback must sit above the tags")
        captureScreen("tag_removed_snackbar")
    }

    @Test
    fun tagsHideWhileTypingAndReturnAboveToolbar() {
        val (activity, keyboard) = showEditor(tags = listOf("Japanese", "Vocabulary"))
        composeRule.onNodeWithText("Japanese").assertDoesNotExist()
        captureScreen("tags_hidden_while_typing")
        hideKeyboard(activity, keyboard)
        val tag =
            composeRule
                .onNodeWithText("Japanese")
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        val bold =
            composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_bold)).fetchSemanticsNode().boundsInRoot
        assertTrue(tag.bottom <= bold.top && bold.top - tag.bottom < 80.dp.toPx(activity))
        captureScreen("tags_above_toolbar")
        fieldOptionsButton().performClick()
        composeRule.onNodeWithText("Vocabulary").assertDoesNotExist()
    }

    @Test
    fun emptyTagsOpenExistingDialog() {
        var opened = false
        val (activity, keyboard) = showEditor(onTags = { opened = true })
        hideKeyboard(activity, keyboard)
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_no_tags)).performClick()
        assertTrue(opened)
        captureScreen("tags_empty")
    }

    @Test
    fun tagRemovalDoesNotOpenDialog() {
        var opened = 0
        var removed: String? = null
        val (activity, keyboard) = showEditor(tags = listOf("Japanese"), onTags = { opened++ }, onRemoveTag = { removed = it })
        hideKeyboard(activity, keyboard)
        composeRule.onNodeWithContentDescription(targetContext.getString(R.string.compose_editor_remove_tag, "Japanese")).performClick()
        assertEquals("Japanese", removed)
        assertEquals(0, opened)
        composeRule.onNodeWithText("Japanese").performClick()
        assertEquals(1, opened)
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_no_tags)).performClick()
        assertEquals(2, opened)
    }

    @Test
    fun tagTextIsCenteredWithTheRemoveIcon() {
        val (activity, keyboard) = showEditor(tags = listOf("Japanese"))
        hideKeyboard(activity, keyboard)
        assertTagTextCentered("Japanese")
        captureScreen("tag_centered_text")
    }

    @Test
    fun threeLongNestedTagSectionsRemainReadable() {
        val (activity, keyboard) = showEditor(tags = listOf(LONG_NESTED_TAG))
        hideKeyboard(activity, keyboard)
        assertTagTextCentered(LONG_NESTED_TAG)
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_no_tags)).assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(targetContext.getString(R.string.compose_editor_remove_tag, LONG_NESTED_TAG))
            .assertIsDisplayed()
        captureScreen("tag_three_long_sections")
        composeRule.onNodeWithText(LONG_NESTED_TAG).performTouchInput { longClick() }
        composeRule.onNode(isPopup()).assertIsDisplayed()
        captureScreen("tag_three_long_sections_tooltip")
    }

    private fun assertTagTextCentered(tag: String) {
        val label = composeRule.onNodeWithText(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val remove =
            composeRule
                .onNodeWithContentDescription(
                    targetContext.getString(R.string.compose_editor_remove_tag, tag),
                    useUnmergedTree = true,
                ).fetchSemanticsNode()
                .boundsInRoot
        assertEquals(remove.center.y, label.center.y, absoluteTolerance = 1f, "Tag text must be centered beside its remove icon")
    }

    @Test
    fun longTagsKeepAddButtonVisible() {
        val (activity, keyboard) = showEditor(tags = listOf(LONG_TAG) + (1..100).map { "Vocabulary::Lesson$it" })
        hideKeyboard(activity, keyboard)
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_no_tags)).assertIsDisplayed()
        captureScreen("tags_long_names")
        composeRule.onNodeWithText(LONG_TAG).performTouchInput { longClick() }
        composeRule.onNode(isPopup()).assertIsDisplayed()
        captureScreen("tag_long_press")
    }

    @Test
    fun tagsRemainVisibleOnTallTabletWhileTyping() {
        setTabletQualifiers()
        showEditor(tags = listOf("Japanese", "Vocabulary"))
        composeRule.onNodeWithText("Japanese").assertIsDisplayed()
        captureScreen("tags_tablet_keyboard")
    }

    @Test
    fun tagsHideInShortTabletWindowWhileTyping() {
        setTabletQualifiers()
        RuntimeEnvironment.setQualifiers("+w800dp-h600dp")
        showEditor(tags = listOf("Japanese", "Vocabulary"))
        composeRule.onNodeWithText("Japanese").assertDoesNotExist()
        captureScreen("tags_tablet_compact_keyboard")
    }

    private fun hideKeyboard(
        activity: AnkiActivity,
        keyboard: View,
    ) {
        composeRule.runOnIdle {
            (keyboard.parent as ViewGroup).removeView(keyboard)
            activity.dispatchInsets(navBarBottom = 48.dp)
        }
        composeRule.waitForIdle()
    }

    @Test
    fun selectedFormatting() {
        showEditor(formats = setOf(WebEditorAction.BOLD, WebEditorAction.UNDERLINE))
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_bold)).assertIsOn()
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_italic)).assertIsOff()
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_underline)).assertIsOn()
        captureScreen("selected_formatting")
    }

    @Test
    fun globalOptionsOpen() {
        showEditor()
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_options)).performClick()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_shrink_images)).assertIsDisplayed()
        captureScreen("global_options")
    }

    @Test
    fun optionsOpen() {
        showEditor()
        val pin = composeRule.onNodeWithText(getResourceString(R.string.compose_editor_pin_field))
        pin.assertDoesNotExist()
        fieldOptionsButton().performClick()
        pin.assertIsDisplayed()
        fieldOptionsButton().performClick()
        pin.assertDoesNotExist()
        fieldOptionsButton().performClick()
        pin.assertIsDisplayed()
        captureScreen("options_open")
    }

    @Test
    fun toolbarConfigurationOpen() {
        openOptions(templates = listOf(EditorCustomToolbarButton(label = "Ruby", prefix = "<ruby>", suffix = "</ruby>")))
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_toolbar)).performClick()
        composeRule.moveToolbarButton("Ruby", targetContext.getString(R.string.compose_editor_move_button_down, "Ruby"))
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_bold)).assertIsDisplayed()
        captureScreen("toolbar_configuration")
    }

    @Test
    fun quickActionsConfigurationOpen() {
        showEditor(toolbar = EditorFieldToolbar(quickActions = listOf("gallery", "pin")))
        fieldOptionsButton().performClick()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_quick_actions)).performClick()
        composeRule.moveToolbarButton(
            getResourceString(R.string.compose_editor_pin_field),
            targetContext.getString(R.string.compose_editor_move_button_up, getResourceString(R.string.compose_editor_pin_field)),
        )
        composeRule
            .onNode(
                hasText(getResourceString(R.string.compose_editor_pin_field)) and
                    SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox),
            ).assertIsOn()
        composeRule.onNodeWithText(getResourceString(R.string.multimedia_editor_image_field_editing_galery)).assertIsOn()
        composeRule.onNodeWithText(getResourceString(R.string.drawing)).assertIsOff()
        captureScreen("quick_actions_configuration")
    }

    @Test
    fun customButtonLibraryOpen() {
        openOptions(templates = listOf(EditorCustomToolbarButton(label = "Ruby", prefix = "<ruby>", suffix = "</ruby>")))
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_custom_buttons)).performClick()
        composeRule.onNodeWithText("Ruby").assertIsDisplayed()
        captureScreen("custom_button_library")
    }

    @Test
    fun customizedToolbar() {
        showEditor(
            toolbar =
                EditorFieldToolbar(
                    hiddenButtons = setOf("BOLD", "ITALIC", "UNDERLINE"),
                    customButtons = listOf(EditorCustomToolbarButton(label = "Ruby", prefix = "<ruby>", suffix = "</ruby>")),
                ),
        )
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_bold)).assertDoesNotExist()
        composeRule.onNodeWithText("Ruby").assertIsDisplayed()
        captureScreen("customized_toolbar")
    }

    @Test
    fun mixedToolbarOrder() {
        showEditor(
            toolbar =
                EditorFieldToolbar(
                    customButtons = listOf(EditorCustomToolbarButton(id = "ruby", label = "Ruby", prefix = "<ruby>", suffix = "</ruby>")),
                    buttonOrder = listOf("BOLD", "custom:ruby", "ITALIC"),
                ),
        )
        val bold =
            composeRule
                .onNodeWithContentDescription(
                    getResourceString(R.string.compose_editor_bold),
                ).fetchSemanticsNode()
                .boundsInRoot
        val custom = composeRule.onNodeWithText("Ruby").fetchSemanticsNode().boundsInRoot
        val italic =
            composeRule
                .onNodeWithContentDescription(
                    getResourceString(R.string.compose_editor_italic),
                ).fetchSemanticsNode()
                .boundsInRoot
        kotlin.test.assertTrue(bold.right <= custom.left && custom.right <= italic.left)
        captureScreen("mixed_toolbar_order")
    }

    @Test
    fun allToolbarButtonsHidden() {
        showEditor(toolbar = EditorFieldToolbar(hiddenButtons = EditorToolbarButton.entries.map { it.name }.toSet()))
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_bold)).assertDoesNotExist()
        captureScreen("all_toolbar_buttons_hidden")
    }

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
    fun longFieldAndNoteTypeNames() {
        openOptions(LONG_FIELD_NAME, LONG_NOTE_TYPE_NAME)
        captureScreen("options_open_long_names")
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_field_options_help)).performClick()
        composeRule
            .onNodeWithText(
                targetContext.getString(R.string.compose_editor_field_options_help_scope, LONG_FIELD_NAME, LONG_NOTE_TYPE_NAME),
            ).assertIsDisplayed()
        captureScreen("help_open_long_names")
    }

    @Test
    fun longDeckName() {
        captureMetadataNames("long_deck_name", deckName = LONG_DECK_NAME)
    }

    @Test
    fun longNoteTypeName() {
        captureMetadataNames("long_note_type_name", noteTypeName = LONG_NOTE_TYPE_NAME)
    }

    @Test
    fun longDeckAndNoteTypeNames() {
        captureMetadataNames("long_deck_and_note_type_names", deckName = LONG_DECK_NAME, noteTypeName = LONG_NOTE_TYPE_NAME)
    }

    @Test
    fun pinLongPress() {
        captureLongPress(R.string.compose_editor_pin_field, R.string.compose_editor_pin_field_summary, "pin_long_press")
    }

    @Test
    fun keyboardLongPress() {
        captureLongPress(R.string.compose_editor_language_hint, R.string.compose_editor_language_hint_summary, "keyboard_long_press")
    }

    @Test
    fun formattingButtonLongPress() {
        captureToolbarLongPress(R.string.compose_editor_bold, "formatting_button_long_press")
    }

    @Test
    fun toolbarActionLongPress() {
        captureToolbarLongPress(com.ichi2.anki.CommonString.undo, "toolbar_action_long_press")
    }

    @Test
    fun editorOptionsLongPress() {
        captureToolbarLongPress(R.string.compose_editor_options, "editor_options_long_press")
    }

    @Test
    fun fieldOptionsLongPress() {
        showEditor()
        fieldOptionsButton().performTouchInput { longClick() }
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_field_options)).assertIsDisplayed()
        captureScreen("field_options_long_press")
    }

    @Test
    fun customButtonLongPress() {
        val label = "Wrap the selection in ruby annotation with pronunciation"
        showEditor(
            toolbar =
                EditorFieldToolbar(
                    customButtons = listOf(EditorCustomToolbarButton(label = label, prefix = "<ruby>", suffix = "</ruby>")),
                ),
        )
        composeRule.onNodeWithText(label).performTouchInput { longClick() }
        composeRule.onNode(hasText(label) and hasAnyAncestor(isPopup())).assertIsDisplayed()
        captureScreen("custom_button_long_press")
    }

    private fun captureToolbarLongPress(
        label: Int,
        name: String,
    ) {
        showEditor()
        val text = getResourceString(label)
        composeRule.onNodeWithContentDescription(text).performTouchInput { longClick() }
        composeRule.onNodeWithText(text).assertIsDisplayed()
        captureScreen(name)
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
        templates: List<EditorCustomToolbarButton> = emptyList(),
    ): Pair<AnkiActivity, View> {
        val editor = showEditor(fieldName = fieldName, noteTypeName = noteTypeName, templates = templates)
        fieldOptionsButton().performClick()
        composeRule.onNodeWithText("Japanese").assertIsDisplayed()
        composeRule.waitForIdle()
        return editor
    }

    private fun fieldOptionsButton() = composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_field_options))

    private fun captureMetadataNames(
        name: String,
        deckName: String = "Default",
        noteTypeName: String = "Basic",
    ) {
        showEditor(deckName = deckName, noteTypeName = noteTypeName)
        composeRule.onNodeWithText(deckName).assertIsDisplayed()
        composeRule.onNodeWithText(noteTypeName).assertIsDisplayed()
        captureScreen(name)
    }

    private fun showEditor(
        fieldName: String = "Back",
        noteTypeName: String = "Basic",
        deckName: String = "Default",
        formats: Set<WebEditorAction> = emptySet(),
        toolbar: EditorFieldToolbar = EditorFieldToolbar(),
        templates: List<EditorCustomToolbarButton> = toolbar.customButtons,
        tags: List<String> = emptyList(),
        onTags: () -> Unit = {},
        onRemoveTag: (String) -> Unit = {},
        snackbarHost: SnackbarHostState = SnackbarHostState(),
    ): Pair<AnkiActivity, View> {
        val activity = startActivityNormallyOpenCollectionWithIntent(AnkiActivity::class.java, Intent())
        activity.setContent {
            AnkiDroidTheme {
                NoteEditorScreen(
                    state =
                        editorState(fieldName, noteTypeName, deckName).let { state ->
                            state.copy(
                                tags = tags,
                                customButtonTemplates = templates,
                                fields =
                                    state.fields.mapIndexed { index, field ->
                                        if (index ==
                                            1
                                        ) {
                                            field.copy(toolbar = toolbar)
                                        } else {
                                            field
                                        }
                                    },
                            )
                        },
                    toolbarState =
                        remember {
                            mutableStateOf(
                                EditorToolbarState(hasSelection = true, activeField = 1, activeFormats = formats),
                            )
                        },
                    ready = true,
                    busy = false,
                    error = null,
                    snackbar = snackbarHost,
                    onBack = {},
                    onSave = {},
                    loadNoteTypes = { emptyList() },
                    onDeck = {},
                    onType = {},
                    onEditType = {},
                    onTags = onTags,
                    onRemoveTag = onRemoveTag,
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
        const val LONG_NESTED_TAG =
            "AdvancedJapaneseVocabularyAndExpressions::" +
                "ReadingPracticeForLiteratureAndNewspapers::" +
                "WordsToRevisitBeforeTheNextProficiencyExam"
        const val LONG_TAG = "Languages::Japanese::AdvancedVocabulary::WordsToRevisitBeforeTheNextProficiencyExam"
        const val LONG_FIELD_NAME = "Japanese reading, pronunciation, pitch accent and detailed usage notes"
        const val LONG_NOTE_TYPE_NAME =
            "Japanese vocabulary with readings, pitch accent, audio, example sentences and detailed grammar explanations"
        const val LONG_DECK_NAME =
            "Languages::Japanese::Advanced vocabulary::Reading practice::Words to revisit before the next proficiency exam"
    }
}
