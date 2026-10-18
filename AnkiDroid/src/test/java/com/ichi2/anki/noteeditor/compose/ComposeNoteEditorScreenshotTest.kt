// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.get
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.descendants
import androidx.core.view.drawToBitmap
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.common.utils.android.getColorFromAttr
import com.ichi2.anki.noteeditor.web.WebEditorAction
import com.ichi2.compose.theme.AnkiDroidTheme
import com.ichi2.compose.theme.toMaterial3ColorScheme
import com.ichi2.testutils.dispatchInsets
import com.ichi2.testutils.simulateKeyboard
import com.ichi2.utils.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
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
    private lateinit var editorActivity: AnkiActivity

    @Test
    fun fieldPanelNavigationFocusesAndTargetsTheDisplayedField() {
        RuntimeEnvironment.setQualifiers("w320dp-h640dp-mdpi")
        val focus = mutableStateOf(EditorToolbarState(hasSelection = true, activeField = 1))
        var descriptionTarget: EditorFieldTarget? = null
        val selected = mutableListOf<Int>()
        val (activity, keyboard) =
            showEditor(
                focusState = focus,
                onDescription = { target, _ -> descriptionTarget = target },
                onFieldSelected = { target ->
                    selected += target.field
                    focus.value = focus.value.copy(activeField = target.field)
                },
            )
        hideKeyboard(activity, keyboard)
        val fields = composeRule.onNodeWithTag("editor_fields").fetchSemanticsNode().boundsInRoot
        openFieldOptions()
        assertTrue(selected.isEmpty(), "Opening options must not reset the existing caret")
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_next_field)).assertIsNotEnabled()
        composeRule.onNodeWithTag("editor_field_pages").performTouchInput { swipeRight() }
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_previous_field)).assertIsNotEnabled()
        composeRule.onNodeWithText("1 of 2").assertIsDisplayed()
        assertEquals(0, focus.value.activeField)
        assertEquals(fields, composeRule.onNodeWithTag("editor_fields").fetchSemanticsNode().boundsInRoot)
        captureScreen("field_panel_previous_field")
        composeRule.onNodeWithTag("editor_field_pages").performTouchInput { swipeLeft() }
        composeRule.onNodeWithText("2 of 2").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_previous_field)).performClick()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_field_description)).performScrollTo().performClick()
        composeRule.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextInput("First field hint")
        composeRule.onNode(hasText(getResourceString(CommonString.save)) and hasAnyAncestor(isDialog())).performClick()
        assertEquals(0, descriptionTarget?.field)
        assertEquals(0, focus.value.activeField)
        assertEquals(listOf(0, 1, 0), selected)
    }

    @Test
    fun fieldPanelSwipesFollowRtlReadingOrder() {
        RuntimeEnvironment.setQualifiers("+ldrtl")
        showEditor()
        openFieldOptions()
        composeRule.onNodeWithTag("editor_field_pages").performTouchInput { swipeLeft() }
        composeRule.onNodeWithText("1 of 2").assertIsDisplayed()
        captureScreen("field_panel_rtl_previous_field")
    }

    @Test
    fun fieldPanelSingleFieldDoesNotOfferNavigation() {
        showEditor(fieldCount = 1, focusState = mutableStateOf(EditorToolbarState(activeField = 0)))
        openFieldOptions()
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_previous_field)).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_next_field)).assertDoesNotExist()
        captureScreen("field_panel_single_field")
    }

    @Test
    fun fieldPanelWith400FieldsCreatesOnlyNearbyControls() {
        showEditor(fieldCount = 400, focusState = mutableStateOf(EditorToolbarState(activeField = 399)))
        composeRule.onNodeWithTag("editor_field_panel").assertDoesNotExist()
        openFieldOptions()
        composeRule.onNodeWithText("400 of 400").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_next_field)).assertIsNotEnabled()
        assertTrue(composeRule.onAllNodes(hasTestTag("editor_field_options")).fetchSemanticsNodes().size <= 2)
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_previous_field)).performClick()
        composeRule.onNodeWithText("399 of 400").assertIsDisplayed()
        captureScreen("field_panel_400_fields")
    }

    @Test
    fun fieldPanelQuickActionsRetainPendingEditsAcrossFields() {
        var saved: EditorFieldPanelDraft? = null
        val (activity, keyboard) =
            showEditor(toolbar = EditorFieldToolbar(quickActions = listOf("gallery", "pin")), onPanelSaved = {
                saved =
                    it
            })
        hideKeyboard(activity, keyboard)
        openFieldCustomization(R.string.compose_editor_quick_actions)
        val gallery = getResourceString(CommonString.multimedia_editor_image_field_editing_galery)
        val pin = getResourceString(R.string.compose_editor_pin_field)
        composeRule.onNodeWithText(gallery).performClick().assertIsNotSelected()
        assertSubscreenStaysOnField("Back")
        returnToFieldOptions()
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_previous_field)).performClick()
        composeRule.onNodeWithText("1 of 2").assertIsDisplayed()
        openFieldSection(R.string.compose_editor_quick_actions)
        composeRule.onNodeWithText(pin).performClick().assertIsNotSelected()
        returnToFieldOptions()
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_next_field)).performClick()
        openFieldSection(R.string.compose_editor_quick_actions)
        composeRule.onNodeWithText(gallery).assertIsNotSelected()
        assertEquals(null, saved)
        captureScreen("field_panel_quick_actions_pending")
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_close_field_options)).performClick()
        composeRule.onNodeWithText(getResourceString(CommonString.discard)).assertIsDisplayed()
        composeRule.onNodeWithText(getResourceString(CommonString.discard)).performClick()
        composeRule.onNodeWithTag("editor_field_panel").assertDoesNotExist()
        assertEquals(null, saved)
        openFieldCustomization(R.string.compose_editor_quick_actions)
        composeRule.onNodeWithText(gallery).assertIsSelected().performClick()
        returnToFieldOptions()
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_previous_field)).performClick()
        openFieldSection(R.string.compose_editor_quick_actions)
        composeRule.onNodeWithText(pin).performClick()
        saveToolbarConfiguration()
        assertEquals(setOf(0, 1), saved?.changedFields()?.keys)
        assertTrue("pin" !in requireNotNull(saved).changedFields().getValue(0).quickActions)
        assertTrue("gallery" !in requireNotNull(saved).changedFields().getValue(1).quickActions)
    }

    @Test
    fun fieldPanelQuickActionReorderDoesNotChangeField() {
        var saved: EditorFieldPanelDraft? = null
        val (activity, keyboard) =
            showEditor(toolbar = EditorFieldToolbar(quickActions = listOf("gallery", "pin")), onPanelSaved = {
                saved =
                    it
            })
        hideKeyboard(activity, keyboard)
        openFieldCustomization(R.string.compose_editor_quick_actions)
        val pin = getResourceString(R.string.compose_editor_pin_field)
        composeRule.moveToolbarButton(pin, targetContext.getString(R.string.compose_editor_move_button_up, pin))
        composeRule
            .onNodeWithContentDescription(targetContext.getString(R.string.compose_editor_field_options_title, "Back"))
            .assertIsDisplayed()
        saveToolbarConfiguration()
        assertEquals(listOf("pin", "gallery"), saved?.changedFields()?.get(1)?.quickActions)
    }

    @Test
    fun fieldPanelOnTabletKeepsThePreviewAndKeyboardSpace() {
        RuntimeEnvironment.setQualifiers("+sw600dp-w900dp-h1000dp")
        val (activity, keyboard) = showEditor(fieldName = LONG_FIELD_NAME, noteTypeName = LONG_NOTE_TYPE_NAME)
        openFieldOptions()
        captureScreen("field_panel_tablet_options")
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_close_field_options)).performClick()
        openFieldCustomization(R.string.compose_editor_toolbar)
        settleMenuTransition(activity)
        captureScreen("field_panel_tablet_toolbar_keyboard")
        hideKeyboard(activity, keyboard)
        captureScreen("field_panel_tablet_toolbar")
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_close_field_options)).performClick()
        openFieldCustomization(R.string.compose_editor_quick_actions)
        settleMenuTransition(activity)
        captureScreen("field_panel_tablet_quick_actions")
    }

    @Test
    fun fieldPanelToolbarUsesOnePendingSharedPreset() {
        var saved: EditorFieldPanelDraft? = null
        val (activity, keyboard) = showEditor(onPanelSaved = { saved = it })
        hideKeyboard(activity, keyboard)
        openFieldCustomization(R.string.compose_editor_toolbar)
        val bold = getResourceString(R.string.compose_editor_bold)
        val italic = getResourceString(R.string.compose_editor_italic)
        composeRule.onNodeWithText(bold).performClick()
        assertSubscreenStaysOnField("Back")
        returnToFieldOptions()
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_previous_field)).performClick()
        openFieldSection(R.string.compose_editor_toolbar)
        composeRule.onNodeWithText(italic).performClick()
        returnToFieldOptions()
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_next_field)).performClick()
        openFieldSection(R.string.compose_editor_toolbar)
        assertEquals(null, saved)
        settleMenuTransition(activity)
        captureScreen("field_panel_shared_toolbar_pending")
        saveToolbarConfiguration()
        assertEquals(setOf("BOLD", "ITALIC"), saved?.presets?.single()?.hiddenButtons)
    }

    private fun returnToFieldOptions() {
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_field_options)).performClick()
    }

    private fun assertSubscreenStaysOnField(field: String) {
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_previous_field)).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_next_field)).assertDoesNotExist()
        composeRule.onNodeWithText("1 of 2").assertDoesNotExist()
        composeRule.onNodeWithText("2 of 2").assertDoesNotExist()
        val heading = composeRule.onNodeWithContentDescription(targetContext.getString(R.string.compose_editor_field_options_title, field))
        composeRule.onNodeWithTag("editor_field_pages").performTouchInput { swipeRight() }
        heading.assertIsDisplayed()
        composeRule.onNodeWithTag("editor_field_pages").performTouchInput { swipeLeft() }
        heading.assertIsDisplayed()
    }

    @Test
    fun fieldToolbarSettingsRetainPresetWhenHidden() {
        val (activity, keyboard) = showEditor(presets = samplePresets())
        hideKeyboard(activity, keyboard)
        openFieldCustomization(R.string.compose_editor_toolbar)
        chooseToolbarPreset("Language learning")
        captureScreen("field_toolbar_preset_selected")
        saveToolbarConfiguration()
        openFieldCustomization(R.string.compose_editor_toolbar)
        composeRule.onNodeWithText("Language learning").assertIsDisplayed()
        val visibility = composeRule.onNodeWithText(getResourceString(R.string.compose_editor_show_field_toolbar))
        visibility.performClick()
        visibility.assertIsOff()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_preset)).assertIsNotEnabled()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_custom_buttons)).assertIsNotEnabled()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_bold)).assertIsNotEnabled()
        composeRule
            .onNodeWithContentDescription(targetContext.getString(R.string.compose_editor_preset_options, "Language learning"))
            .assertIsNotEnabled()
        captureScreen("field_toolbar_preset_hidden")
        saveToolbarConfiguration()
        toolbarOverflowButton().assertDoesNotExist()
        openFieldCustomization(R.string.compose_editor_toolbar)
        composeRule.onNodeWithText("Language learning").assertIsDisplayed()
        visibility.performClick()
        visibility.assertIsOn()
        chooseToolbarPreset(getResourceString(R.string.compose_editor_default_toolbar))
        saveToolbarConfiguration()
        toolbarOverflowButton().assertDoesNotExist()
    }

    private fun chooseToolbarPreset(name: String) {
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_preset)).performClick()
        composeRule.onNode(hasText(name) and hasAnyAncestor(isPopup())).performClick()
        composeRule.onNode(isPopup()).assertDoesNotExist()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_preset)).assertTextContains(name)
    }

    private fun assertPanelAboveKeyboard(keyboard: View) {
        val keyboardLocation = IntArray(2)
        keyboard.getLocationInWindow(keyboardLocation)
        val panel = composeRule.onNodeWithTag("editor_field_panel").fetchSemanticsNode().boundsInWindow
        assertTrue(
            panel.bottom <= keyboardLocation[1] + 1,
            "Panel controls must stay above the keyboard: $panel, keyboard y=${keyboardLocation[1]}",
        )
    }

    private fun scrollToolbarHeaderTo(
        activity: AnkiActivity,
        label: Int,
    ) {
        val bounds = composeRule.onNodeWithTag("editor_toolbar_list").fetchSemanticsNode().boundsInWindow
        val target = composeRule.onNodeWithText(getResourceString(label)).fetchSemanticsNode().boundsInWindow
        composeRule.runOnIdle {
            val list = (activity.window.decorView as ViewGroup).descendants.filterIsInstance<RecyclerView>().first { it.isShown }
            list.scrollBy(0, (target.top - bounds.top).roundToInt())
        }
        composeRule.waitForIdle()
    }

    private fun saveToolbarConfiguration() {
        composeRule
            .onNode(
                hasContentDescription(getResourceString(CommonString.save)) and hasAnyAncestor(hasTestTag("editor_field_panel")),
            ).performClick()
    }

    @Test
    fun globalHiddenLayoutKeepsFieldToolbarSettingsAccessible() {
        val (activity, keyboard) = showEditor(toolbarDisplay = EditorToolbarDisplay.HIDDEN)
        hideKeyboard(activity, keyboard)
        openFieldCustomization(R.string.compose_editor_toolbar)
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_toolbar_hidden_globally)).assertIsDisplayed()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_show_field_toolbar)).assertIsOn()
        composeRule
            .onNodeWithText(getResourceString(R.string.compose_editor_preset))
            .assertIsDisplayed()
            .assertTextContains(getResourceString(R.string.compose_editor_default_toolbar))
        captureScreen("field_toolbar_hidden_globally")
    }

    @Test
    fun stackedLayoutKeepsFieldToolbarSettingsAccessible() {
        showEditor(toolbarDisplay = EditorToolbarDisplay.STACK)
        openFieldCustomization(R.string.compose_editor_toolbar)
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_show_field_toolbar)).assertIsOn()
        composeRule
            .onNodeWithText(getResourceString(R.string.compose_editor_preset))
            .assertIsDisplayed()
            .assertTextContains(getResourceString(R.string.compose_editor_default_toolbar))
    }

    @Test
    fun toolbarVisibilityFollowsTheFocusedField() {
        val focus = mutableStateOf(EditorToolbarState(activeField = 1))
        showEditor(toolbar = EditorFieldToolbar(showToolbar = false), focusState = focus)
        val bold = composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_bold))
        bold.assertDoesNotExist()
        composeRule.runOnIdle { focus.value = focus.value.copy(activeField = 0) }
        bold.assertIsDisplayed()
        composeRule.runOnIdle { focus.value = focus.value.copy(activeField = 1) }
        bold.assertDoesNotExist()
    }

    @Test
    fun rightToLeftFieldOptions() {
        var requested: Pair<EditorFieldTarget, Boolean>? = null
        showEditor(rtl = true, onDirection = { target, rtl -> requested = target to rtl })
        openFieldOptions()
        val toggle = composeRule.onNodeWithText(getResourceString(R.string.compose_editor_right_to_left))
        toggle.performScrollTo().assertIsOn()
        captureScreen("field_options_right_to_left")
        toggle.performClick()
        assertEquals(1, requested?.first?.field)
        assertEquals(false, requested?.second)
    }

    @Test
    fun fieldOptionsGridUsesTwoEqualColumns() {
        showEditor()
        openFieldOptions()
        val options =
            listOf(
                EditorFieldOption.PIN,
                EditorFieldOption.HTML,
                EditorFieldOption.COLLAPSED,
                EditorFieldOption.RIGHT_TO_LEFT,
                EditorFieldOption.FONT,
                EditorFieldOption.KEYBOARD,
            )
        val bounds =
            options.map {
                composeRule
                    .onNodeWithTag("editor_field_option_${it.name}")
                    .assertIsDisplayed()
                    .fetchSemanticsNode()
                    .boundsInRoot
            }
        for ((left, right) in bounds.chunked(2)) {
            assertEquals(left.top, right.top, 1f)
            assertEquals(left.bottom, right.bottom, 1f)
            assertEquals(left.width, right.width, 1f)
            assertEquals(
                bounds.first().height,
                left.height,
                targetContext.resources.displayMetrics.density,
                "Normal-size labels should fit equally compact rows (within 1dp of font rounding)",
            )
            assertTrue(left.right < right.left)
            assertEquals(bounds.first().left, left.left, 1f)
            assertEquals(bounds[1].right, right.right, 1f)
        }
        val description =
            composeRule
                .onNodeWithText(
                    getResourceString(R.string.compose_editor_field_description),
                ).fetchSemanticsNode()
                .boundsInRoot
        assertTrue(description.top >= bounds.last().bottom, "Description belongs below the six tiles")
        captureScreen("field_options_grid")
    }

    @Test
    fun keyboardLanguageOverrideHighlightsTheSettingsLink() {
        val (activity, _) = showEditor()
        openFieldOptions()
        val keyboard = composeRule.onNodeWithTag("editor_field_option_KEYBOARD")
        val pixels = keyboard.captureToImage().toPixelMap()
        assertEquals(activity.toMaterial3ColorScheme().primaryContainer.toArgb(), pixels[pixels.width / 2, 4].toArgb())
        keyboard.assertHasClickAction().assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ToggleableState))
        composeRule.onNodeWithText("Japanese").assertIsDisplayed()
        captureScreen("field_keyboard_language_override")
    }

    @Test
    fun defaultKeyboardLanguageKeepsTheNeutralSettingsLink() {
        val (activity, _) =
            showEditor(
                deckName = "Test deck",
                focusState = mutableStateOf(EditorToolbarState(hasSelection = true, activeField = 0)),
            )
        openFieldOptions()
        val keyboard = composeRule.onNodeWithTag("editor_field_option_KEYBOARD")
        val pixels = keyboard.captureToImage().toPixelMap()
        assertEquals(activity.toMaterial3ColorScheme().surface.toArgb(), pixels[pixels.width / 2, 4].toArgb())
        keyboard.assertHasClickAction().assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ToggleableState))
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_language_hint_default)).assertIsDisplayed()
        captureScreen("field_keyboard_language_default")
    }

    @Test
    fun fieldOptionsGridAllTogglesEnabled() {
        showEditor(
            rtl = true,
            htmlDefaultState = mutableStateOf(true),
            collapsedState = mutableStateOf(true),
            description = "Write an example sentence and explain its meaning, including the context in which you found it.",
        )
        openFieldOptions()
        for (option in listOf(
            EditorFieldOption.PIN,
            EditorFieldOption.HTML,
            EditorFieldOption.COLLAPSED,
            EditorFieldOption.RIGHT_TO_LEFT,
        )) {
            composeRule.onNodeWithTag("editor_field_option_${option.name}").assertIsOn()
        }
        captureScreen("field_options_grid_all_enabled")
    }

    @Test
    fun fieldOptionsGridAllTogglesOff() {
        showEditor(pinned = false)
        openFieldOptions()
        for (option in listOf(
            EditorFieldOption.PIN,
            EditorFieldOption.HTML,
            EditorFieldOption.COLLAPSED,
            EditorFieldOption.RIGHT_TO_LEFT,
        )) {
            composeRule.onNodeWithTag("editor_field_option_${option.name}").assertIsOff()
        }
        captureScreen("field_options_grid_all_off")
    }

    @Test
    fun fieldOptionTilesWithMixedStates() {
        val (activity, keyboard) = showEditor(rtl = true, htmlDefaultState = mutableStateOf(true))
        hideKeyboard(activity, keyboard)
        openFieldOptions()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_right_to_left)).assertIsOn()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_start_in_html_mode)).assertIsOn()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_start_collapsed)).assertIsOff()
        captureScreen("field_options_mixed_tiles")
    }

    @Test
    fun htmlByDefaultFieldOptions() {
        val htmlDefault = mutableStateOf(false)
        val requested = mutableListOf<Pair<EditorFieldTarget, Boolean>>()
        showEditor(
            sourceMode = true, // The saved default is independent of the active input mode.
            htmlDefaultState = htmlDefault,
            onHtmlByDefault = { target, value ->
                requested.add(target to value)
                htmlDefault.value = value
            },
        )
        openFieldOptions()
        val toggle = composeRule.onNodeWithText(getResourceString(R.string.compose_editor_start_in_html_mode))
        toggle.performScrollTo().assertIsOff()
        captureScreen("field_options_html_default_off")
        toggle.performClick().assertIsOn()
        captureScreen("field_options_html_default_on")
        toggle.performClick().assertIsOff()
        assertEquals(listOf(true, false), requested.map { it.second })
        assertTrue(requested.all { it.first == EditorFieldTarget("screenshot", 0, 1) })
    }

    @Test
    fun collapseByDefaultFieldOptions() {
        val collapsed = mutableStateOf(false)
        val requested = mutableListOf<Pair<EditorFieldTarget, Boolean>>()
        showEditor(
            collapsedState = collapsed,
            onCollapseByDefault = { target, value ->
                requested.add(target to value)
                collapsed.value = value
            },
        )
        openFieldOptions()
        val toggle = composeRule.onNodeWithText(getResourceString(R.string.compose_editor_start_collapsed))
        toggle.performScrollTo().assertIsOff()
        captureScreen("field_options_collapse_default_off")
        toggle.performClick().assertIsOn()
        captureScreen("field_options_collapse_default_on")
        toggle.performClick().assertIsOff()
        assertEquals(listOf(true, false), requested.map { it.second })
        assertTrue(requested.all { it.first == EditorFieldTarget("screenshot", 0, 1) })
    }

    @Test
    fun fieldDescriptionSettings() {
        RuntimeEnvironment.setQualifiers("w320dp-h640dp-mdpi")
        val changes = mutableListOf<Pair<EditorFieldTarget, String>>()
        val (activity, keyboard) = showEditor(onDescription = { target, value -> changes.add(target to value) })
        hideKeyboard(activity, keyboard)
        openFieldOptions()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_field_description)).performScrollTo().performClick()
        val input = composeRule.onNode(hasSetTextAction() and hasAnyAncestor(isDialog()))
        input.assertIsDisplayed()
        captureScreen("field_description_empty")
        val hint = "Write a sentence using this word, then explain its meaning. Include an example from a book or a conversation."
        input.performTextInput(hint)
        input.assertTextContains(hint)
        captureScreen("field_description_long")
        composeRule.onNode(hasText(getResourceString(CommonString.save)) and hasAnyAncestor(isDialog())).performClick()
        assertEquals(EditorFieldTarget("screenshot", 0, 1), changes.single().first)
        assertTrue(changes.single().second.startsWith("Write a sentence"))
    }

    @Test
    fun fieldDescriptionCanBeClearedOrCancelled() {
        RuntimeEnvironment.setQualifiers("w320dp-h640dp-mdpi")
        val changes = mutableListOf<String>()
        val (activity, keyboard) = showEditor(description = "An existing hint", onDescription = { _, value -> changes.add(value) })
        hideKeyboard(activity, keyboard)
        openFieldOptions()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_field_description)).performScrollTo().performClick()
        val input = composeRule.onNode(hasSetTextAction() and hasAnyAncestor(isDialog()))
        input.assertTextContains("An existing hint").performTextReplacement("")
        composeRule.onNodeWithText(getResourceString(CommonString.dialog_cancel)).performClick()
        assertTrue(changes.isEmpty())
        composeRule.onNodeWithText(getResourceString(CommonString.discard)).assertIsDisplayed()
        captureScreen("discard_field_description_changes")
        composeRule.onNodeWithText(getResourceString(CommonString.discard)).performClick()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_field_description)).performScrollTo().performClick()
        input.assertTextContains("An existing hint").performTextReplacement("")
        composeRule.onNode(hasText(getResourceString(CommonString.save)) and hasAnyAncestor(isDialog())).performClick()
        assertEquals(listOf(""), changes)
    }

    @Test
    fun fieldFontSettings() {
        // Match EquationDialogScreenshotTest: wider text-field dialogs hit Robolectric issue 8460.
        RuntimeEnvironment.setQualifiers("w320dp-h640dp-mdpi")
        showEditor()
        openFieldOptions()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_field_font)).performScrollTo().performClick()
        composeRule
            .onNode(hasText(getResourceString(R.string.compose_editor_field_font)) and hasAnyAncestor(isDialog()) and !hasSetTextAction())
            .assertIsDisplayed()
        captureScreen("field_font_settings")
    }

    @Test
    fun tintedBackgroundKeepsMetadataReadable() {
        val (activity, _) = showEditor()
        val image = activity.window.decorView.drawToBitmap()
        for (label in listOf("Default", "Basic")) {
            val bounds = composeRule.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInWindow
            val background = image.getPixel(bounds.center.x.roundToInt(), bounds.top.toInt() - 1)
            val contrast =
                (bounds.left.toInt() until bounds.right.toInt()).maxOf { x ->
                    (bounds.top.toInt() until bounds.bottom.toInt()).maxOf { y ->
                        ColorUtils.calculateContrast(image.getPixel(x, y), background)
                    }
                }
            assertTrue(contrast >= 4.5, "$label must remain readable on the tinted background: contrast=$contrast")
        }
        captureScreen("tinted_metadata_contrast")
    }

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
        openFieldOptions()
        composeRule.onNodeWithText("Vocabulary").assertDoesNotExist()
    }

    @Test
    fun emptyTagsOpenExistingDialog() {
        var opened = 0
        val (activity, keyboard) = showEditor(onTags = { opened++ })
        hideKeyboard(activity, keyboard)
        val addTags = composeRule.onNodeWithText(getResourceString(R.string.compose_editor_no_tags))
        val centerY =
            addTags
                .fetchSemanticsNode()
                .boundsInRoot.center.y
        composeRule.onRoot().performTouchInput { click(Offset(width - 1f, centerY)) }
        assertEquals(1, opened, "The far end of the empty row must open the tags dialog")
        composeRule.onRoot().performTouchInput { click(Offset(1f, centerY)) }
        assertEquals(2, opened, "The start of the empty row must open the tags dialog")
        addTags.performClick()
        assertEquals(3, opened)
        captureScreen("tags_empty")
    }

    @Test
    fun removingTheLastTagKeepsTheLeadingContentAligned() {
        val tags = mutableStateListOf("Japanese")
        val (activity, keyboard) = showEditor(tags = tags)
        hideKeyboard(activity, keyboard)
        val populatedLeft =
            composeRule
                .onNodeWithText(
                    getResourceString(CommonString.card_details_tags),
                    useUnmergedTree = true,
                ).fetchSemanticsNode()
                .boundsInRoot.left
        composeRule.runOnIdle { tags.clear() }
        val emptyLeft =
            composeRule
                .onNodeWithText(
                    getResourceString(R.string.compose_editor_no_tags),
                    useUnmergedTree = true,
                ).fetchSemanticsNode()
                .boundsInRoot.left
        assertEquals(populatedLeft, emptyLeft, "The leading icon and label must stay aligned when the last tag is removed")
    }

    @Test
    fun removingTheLastTagKeepsTheIconColor() {
        val tags = mutableStateListOf("Japanese")
        val (activity, keyboard) = showEditor(tags = tags)
        hideKeyboard(activity, keyboard)

        fun iconColor(label: String): Int {
            val text = composeRule.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInWindow
            val image = activity.window.decorView.drawToBitmap()
            val y = text.center.y.roundToInt()
            val color = image.getPixel((text.left - 18.dp.toPx(activity)).roundToInt(), y)
            assertNotEquals(image.getPixel(0, y), color, "The sampled pixel must be inside the tag icon")
            return color
        }
        val populatedColor = iconColor(getResourceString(CommonString.card_details_tags))
        composeRule.runOnIdle { tags.clear() }
        val emptyColor = iconColor(getResourceString(R.string.compose_editor_no_tags))
        assertEquals(populatedColor, emptyColor, "The tag icon must keep its color when the last tag is removed")
    }

    @Test
    fun populatedTagLabelAndUnusedSpaceOpenExistingDialog() {
        var opened = 0
        val (activity, keyboard) = showEditor(tags = listOf("Japanese"), onTags = { opened++ })
        hideKeyboard(activity, keyboard)
        composeRule.onNodeWithText(getResourceString(CommonString.card_details_tags), useUnmergedTree = true).performTouchInput { click() }
        assertEquals(1, opened, "Tapping Tags must open the dialog")
        val tag = composeRule.onNodeWithText("Japanese").fetchSemanticsNode().boundsInRoot
        val add =
            composeRule
                .onNodeWithContentDescription(getResourceString(R.string.compose_editor_no_tags))
                .fetchSemanticsNode()
                .boundsInRoot
        assertTrue(tag.right < add.left, "There must be unused space after the tag in this fixture")
        composeRule.onRoot().performTouchInput { click(Offset((tag.right + add.left) / 2, tag.center.y)) }
        assertEquals(2, opened, "Tapping unused space after the tags must open the dialog")
        captureScreen("tags_populated_clickable")
    }

    @Test
    fun tagRemovalDoesNotOpenDialog() {
        var opened = 0
        var removed: String? = null
        val (activity, keyboard) = showEditor(tags = listOf("Japanese"), onTags = { opened++ }, onRemoveTag = { removed = it })
        hideKeyboard(activity, keyboard)
        composeRule
            .onNodeWithContentDescription(
                targetContext.getString(R.string.compose_editor_remove_tag, "Japanese"),
            ).performTouchInput {
                click()
            }
        assertEquals("Japanese", removed)
        assertEquals(0, opened)
        composeRule.onNodeWithText("Japanese").performTouchInput { click() }
        assertEquals(1, opened)
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_no_tags)).performTouchInput { click() }
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
    fun shortTagHasBalancedPaddingAndSeparateRemoveAction() {
        var opened = 0
        var removed: String? = null
        val (activity, keyboard) = showEditor(tags = listOf("y"), onTags = { opened++ }, onRemoveTag = { removed = it })
        hideKeyboard(activity, keyboard)
        val chip = composeRule.onNodeWithText("y").fetchSemanticsNode().boundsInRoot
        val label = composeRule.onNodeWithText("y", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val remove =
            composeRule
                .onNodeWithContentDescription(
                    targetContext.getString(R.string.compose_editor_remove_tag, "y"),
                    useUnmergedTree = true,
                ).fetchSemanticsNode()
                .boundsInRoot
        assertEquals(
            label.left - chip.left,
            chip.right - remove.right,
            absoluteTolerance = 4.dp.toPx(activity).toFloat(),
            "The remove icon should not leave a much larger end inset than the label's start inset",
        )
        composeRule.onRoot().performTouchInput { click(label.center) }
        assertEquals(1, opened, "Tapping a short tag's text must open the dialog")
        assertEquals(null, removed)
        composeRule.onRoot().performTouchInput { click(remove.center) }
        assertEquals("y", removed)
        assertEquals(1, opened, "The remove action must not open the dialog")
        assertTagTextCentered("y")
        captureScreen("tag_short_balanced_padding")
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
    @RequiresApi(26)
    fun selectedFormatting() {
        val (activity, _) = showEditor(formats = setOf(WebEditorAction.BOLD, WebEditorAction.UNDERLINE))
        val controlColor = getColorFromAttr(activity, android.R.attr.colorControlNormal)
        for (label in listOf(
            R.string.compose_editor_options,
            CommonString.card_editor_preview_card,
            androidx.appcompat.R.string.abc_action_bar_up_description,
        )) {
            val description = getResourceString(label)
            val pixels = composeRule.onNodeWithContentDescription(description).captureToImage().toPixelMap()
            val renderedColor = ColorUtils.compositeColors(controlColor, pixels[0, 0].toArgb())
            assertTrue(
                (0 until pixels.width).any { x -> (0 until pixels.height).any { y -> pixels[x, y].toArgb() == renderedColor } },
                "$description must use the theme's colorControlNormal",
            )
        }
        val italic =
            composeRule
                .onNodeWithContentDescription(
                    getResourceString(R.string.compose_editor_italic),
                ).captureToImage()
                .toPixelMap()
        val textColor = activity.toMaterial3ColorScheme().onSurface.toArgb()
        assertTrue(
            (0 until italic.width).any { x -> (0 until italic.height).any { y -> italic[x, y].toArgb() == textColor } },
            "Formatting letterforms use text contrast",
        )
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_bold)).assertIsOn()
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_italic)).assertIsOff()
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_underline)).assertIsOn()
        captureScreen("selected_formatting")
    }

    @Test
    fun mathJaxIconKeepsEnabledAndDisabledStatesDistinct() {
        val focus = mutableStateOf(EditorToolbarState(hasSelection = true, activeField = 1))
        val (activity, _) =
            showEditor(
                toolbarDisplay = EditorToolbarDisplay.STACK,
                toolbar =
                    EditorFieldToolbar(
                        hiddenButtons = EditorToolbarButton.entries.map { it.name }.toSet() - setOf("BOLD", "EQUATIONS", "RECORD_AUDIO"),
                    ),
                focusState = focus,
            )
        val equation = composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_equations))
        val enabledColor = activity.toMaterial3ColorScheme().onSurface.toArgb()
        val enabledPixels = equation.assertIsEnabled().captureToImage().toPixelMap()
        assertTrue(
            (0 until enabledPixels.width).any { x ->
                (0 until enabledPixels.height).any { y -> enabledPixels[x, y].toArgb() == enabledColor }
            },
            "The fine MathJax strokes need the theme's text contrast when enabled",
        )
        captureScreen("mathjax_toolbar_icon_enabled")
        composeRule.runOnIdle { focus.value = focus.value.copy(hasSelection = false) }
        val disabledPixels = equation.assertIsNotEnabled().captureToImage().toPixelMap()
        val disabledColor =
            activity
                .toMaterial3ColorScheme()
                .onSurface
                .copy(alpha = 0.38f)
                .compositeOver(disabledPixels[0, 0])
                .toArgb()
        assertNotEquals(enabledColor, disabledColor)
        assertTrue(
            (0 until disabledPixels.width).any { x ->
                (0 until disabledPixels.height).any { y -> disabledPixels[x, y].toArgb() == disabledColor }
            },
            "An unavailable MathJax action must retain Material's disabled appearance",
        )
        captureScreen("mathjax_toolbar_icon_disabled")
    }

    @Test
    fun scrollToolbarUsesTheFullWidthWithoutASettingsMenu() {
        showEditor()
        toolbarOverflowButton().assertDoesNotExist()
        val toolbar = composeRule.onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot
        val screen = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals(screen.left, toolbar.left, 1f)
        assertEquals(screen.right, toolbar.right, 1f)
        captureScreen("scroll_toolbar_without_settings_menu")
    }

    @Test
    fun globalOptionsOpen() {
        showEditor()
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_options)).performClick()
        for (label in listOf(
            CommonString.image_occlusion,
            R.string.compose_editor_shrink_images,
            R.string.compose_editor_mathjax_preview,
            R.string.compose_editor_auto_close_tags,
            R.string.compose_editor_custom_buttons,
            R.string.compose_editor_toolbar,
            R.string.compose_editor_quick_actions,
        )) {
            composeRule.onNodeWithText(getResourceString(label)).assertDoesNotExist()
        }
        composeRule.onNodeWithText("Editor text size").assertDoesNotExist()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_toolbar_layout)).assertIsDisplayed()
        val labels =
            listOf(
                R.string.compose_editor_field_options,
                R.string.compose_editor_toolbar_layout,
                R.string.compose_editor_fields,
                R.string.compose_editor_cards,
            )
        val tops =
            labels.map { label ->
                composeRule
                    .onNodeWithText(getResourceString(label))
                    .assertIsDisplayed()
                    .fetchSemanticsNode()
                    .boundsInRoot.top
            }
        assertEquals(tops.sorted(), tops, "Group field settings, editor appearance and note-type editing in that order")
        captureScreen("global_options")
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_field_options)).performClick()
        openFieldSection(R.string.compose_editor_toolbar)
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_custom_buttons)).assertIsDisplayed()
    }

    @Test
    fun globalOptionsKeepUnavailableFieldSettingsVisible() {
        showEditor(focusState = mutableStateOf(EditorToolbarState(hasSelection = false, activeField = null)))
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_options)).performClick()
        for (label in listOf(
            R.string.compose_editor_field_options,
        )) {
            composeRule.onNodeWithText(getResourceString(label)).assertIsDisplayed().assertIsNotEnabled()
        }
        for (label in listOf(
            R.string.compose_editor_toolbar_layout,
            R.string.compose_editor_fields,
            R.string.compose_editor_cards,
        )) {
            composeRule.onNodeWithText(getResourceString(label)).assertIsEnabled()
        }
        captureScreen("global_options_without_focused_field")
    }

    @Test
    fun globalOptionsKeepIconAndLabelColumnsAligned() {
        showEditor(toolbarDisplay = EditorToolbarDisplay.STACK)
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_options)).performClick()
        val labels =
            listOf(
                R.string.compose_editor_toolbar_layout,
                R.string.compose_editor_field_options,
                R.string.compose_editor_fields,
                R.string.compose_editor_cards,
            )
        val leftEdges =
            labels.map { label ->
                composeRule
                    .onNodeWithText(getResourceString(label), useUnmergedTree = true)
                    .assertIsDisplayed()
                    .fetchSemanticsNode()
                    .boundsInRoot.left
            }
        leftEdges.forEach { left ->
            assertEquals(leftEdges.first(), left, 1f, "Every menu label must align after its leading icon")
        }
        captureScreen("global_options_with_icons")
    }

    @Test
    fun mainMenuOpensMenuBeforeFieldOptions() {
        showEditor()
        mainMenuButton().performClick()
        composeRule
            .onNode(
                hasText(getResourceString(R.string.compose_editor_field_options)) and hasAnyAncestor(isPopup()),
            ).assertIsDisplayed()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_toolbar)).assertDoesNotExist()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_quick_actions)).assertDoesNotExist()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_pin_field)).assertDoesNotExist()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_field_options)).performClick()
        composeRule.onNode(isPopup()).assertDoesNotExist()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_pin_field)).assertIsDisplayed()
    }

    @Test
    fun mainMenuWithKeyboard() {
        showEditor()
        mainMenuButton().performClick()
        composeRule.onNode(isPopup()).assertIsDisplayed()
        captureScreen("main_menu_with_keyboard")
    }

    @Test
    fun mainMenuWithoutKeyboard() {
        val (activity, keyboard) = showEditor()
        hideKeyboard(activity, keyboard)
        mainMenuButton().performClick()
        composeRule.onNode(isPopup()).assertIsDisplayed()
        captureScreen("main_menu_without_keyboard")
    }

    @Test
    fun mainMenuClosesWhenTheFieldChanges() {
        val focus = mutableStateOf(EditorToolbarState(hasSelection = true, activeField = 1))
        showEditor(focusState = focus)
        mainMenuButton().performClick()
        composeRule.onNode(isPopup()).assertIsDisplayed()
        composeRule.runOnIdle { focus.value = focus.value.copy(activeField = 0) }
        composeRule.onNode(isPopup()).assertDoesNotExist()
        mainMenuButton().performClick()
        composeRule.onNode(isPopup()).assertIsDisplayed()
        composeRule.runOnIdle { focus.value = focus.value.copy(activeField = null) }
        composeRule.onNode(isPopup()).assertDoesNotExist()
        mainMenuButton().assertIsEnabled()
    }

    @Test
    fun fieldOptionsOpenQuickActions() {
        showEditor()
        openFieldCustomization(R.string.compose_editor_quick_actions)
        composeRule
            .onNode(
                hasText(getResourceString(R.string.compose_editor_quick_actions)) and hasAnyAncestor(hasTestTag("editor_field_panel")),
            ).assertIsDisplayed()
        composeRule.onNode(isPopup()).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_close_field_options)).performClick()
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_close_field_options)).assertDoesNotExist()
        toolbarOverflowButton().assertDoesNotExist()
    }

    @Test
    fun fieldOptionsOpenToolbarPresets() {
        showEditor()
        openFieldCustomization(R.string.compose_editor_toolbar)
        composeRule
            .onNode(
                hasText(getResourceString(R.string.compose_editor_toolbar)) and hasAnyAncestor(hasTestTag("editor_field_panel")),
            ).assertIsDisplayed()
        composeRule.onNode(isPopup()).assertDoesNotExist()
    }

    @Test
    fun fieldOptionsIncludeToolbarAndQuickActions() {
        val (activity, keyboard) = showEditor()
        hideKeyboard(activity, keyboard)
        openFieldOptions()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_toolbar)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_quick_actions)).performScrollTo().assertIsDisplayed()
        captureScreen("field_options_customization_links")
    }

    @Test
    fun fieldOptionsCustomizationKeepsTheDisplayedFieldAndPendingEdits() {
        val focus = mutableStateOf(EditorToolbarState(hasSelection = true, activeField = 1))
        var saved: EditorFieldPanelDraft? = null
        val (activity, keyboard) = showEditor(focusState = focus, onPanelSaved = { saved = it })
        val editor = composeRule.onNodeWithTag("editor_fields").fetchSemanticsNode().boundsInRoot
        openFieldOptions()
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_previous_field)).performClick()
        val panel = composeRule.onNodeWithTag("editor_field_panel").fetchSemanticsNode().id
        openFieldSection(R.string.compose_editor_toolbar)
        assertSubscreenStaysOnField("Front")
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_bold)).performClick()
        assertPanelAboveKeyboard(keyboard)
        captureScreen("field_options_toolbar_section")
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_field_options)).performClick()
        composeRule.onNodeWithTag("editor_field_options").assertExists()
        openFieldSection(R.string.compose_editor_quick_actions)
        assertSubscreenStaysOnField("Front")
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_pin_field)).performClick()
        assertPanelAboveKeyboard(keyboard)
        captureScreen("field_options_quick_actions_section")
        composeRule.runOnIdle { activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithTag("editor_field_options").assertExists()
        assertEquals(panel, composeRule.onNodeWithTag("editor_field_panel").fetchSemanticsNode().id)
        assertEquals(editor, composeRule.onNodeWithTag("editor_fields").fetchSemanticsNode().boundsInRoot)
        assertEquals(1, focus.value.activeField, "Browsing settings must not move the caret")
        assertEquals(null, saved, "Returning to Field options must not save or discard pending edits")
        captureScreen("field_options_with_pending_customization")
        saveToolbarConfiguration()
        assertEquals(setOf("BOLD"), saved?.presets?.single()?.hiddenButtons)
        assertEquals(setOf(0), saved?.changedFields()?.keys)
        assertTrue("pin" !in requireNotNull(saved).changedFields().getValue(0).quickActions)
    }

    @Test
    fun stackedToolbarKeepsQuickActionsAccessible() = checkQuickActionsInGeneralOptions(EditorToolbarDisplay.STACK)

    @Test
    fun hiddenToolbarKeepsQuickActionsAccessible() = checkQuickActionsInGeneralOptions(EditorToolbarDisplay.HIDDEN)

    @Test
    fun fieldWithHiddenToolbarKeepsQuickActionsAccessible() = checkQuickActionsInGeneralOptions(EditorToolbarDisplay.SCROLL, false)

    private fun checkQuickActionsInGeneralOptions(
        display: EditorToolbarDisplay,
        fieldToolbarVisible: Boolean = true,
    ) {
        showEditor(toolbarDisplay = display, toolbar = EditorFieldToolbar(showToolbar = fieldToolbarVisible))
        toolbarOverflowButton().assertDoesNotExist()
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_options)).performClick()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_toolbar)).assertDoesNotExist()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_custom_buttons)).assertDoesNotExist()
        captureScreen(if (fieldToolbarVisible) "global_options_${display.name.lowercase()}" else "global_options_field_toolbar_hidden")
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_field_options)).performClick()
        openFieldSection(R.string.compose_editor_quick_actions)
        composeRule
            .onNode(hasText(getResourceString(R.string.compose_editor_quick_actions)) and hasAnyAncestor(hasTestTag("editor_field_panel")))
            .assertIsDisplayed()
    }

    @Test
    fun optionsOpen() {
        showEditor()
        val pin = composeRule.onNodeWithText(getResourceString(R.string.compose_editor_pin_field))
        pin.assertDoesNotExist()
        openFieldOptions()
        pin.assertIsDisplayed()
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_close_field_options)).performClick()
        pin.assertDoesNotExist()
        openFieldOptions()
        pin.assertIsDisplayed()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_custom_buttons)).assertDoesNotExist()
        captureScreen("options_open")
    }

    @Test
    fun optionsOpenWithoutKeyboard() {
        val (activity, keyboard) = showEditor()
        hideKeyboard(activity, keyboard)
        openFieldOptions()
        composeRule.onNodeWithText("Japanese").assertIsDisplayed()
        settleMenuTransition(activity)
        captureScreen("options_open_without_keyboard")
    }

    @Test
    fun fieldOptionsFinishSlidingOutBeforeRemovingContent() {
        var optionsVisible = false
        showEditor(onFieldOptionsVisibility = { optionsVisible = it })
        val editorBottom =
            composeRule
                .onNodeWithContentDescription(
                    getResourceString(R.string.compose_editor_bold),
                ).fetchSemanticsNode()
                .boundsInRoot.bottom
        openFieldOptions()
        composeRule.waitForIdle()
        val close = hasContentDescription(getResourceString(R.string.compose_editor_close_field_options))
        var lastTop =
            composeRule
                .onNode(close)
                .fetchSemanticsNode()
                .positionInRoot.y
        var removed = false
        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.onNode(close).performClick()
            for (frame in 1..90) {
                composeRule.mainClock.advanceTimeByFrame()
                val button = composeRule.onAllNodes(close).fetchSemanticsNodes().singleOrNull()
                if (button == null) {
                    assertTrue(lastTop >= editorBottom - 1, "Sheet content vanished at $lastTop before sliding below $editorBottom")
                    removed = true
                    break
                }
                assertTrue(optionsVisible, "Keep native cursor handles hidden throughout the closing animation")
                lastTop = button.positionInRoot.y
            }
            assertTrue(removed, "The sheet must finish closing")
            assertEquals(false, optionsVisible, "Restore native cursor handling after the sheet leaves")
            composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_bold)).assertIsDisplayed()
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun fieldOptionsRemainReachableAbovePixelKeyboard() {
        RuntimeEnvironment.setQualifiers("+w427dp-h952dp")
        val (activity, keyboard) = showEditor()
        hideKeyboard(activity, keyboard)
        val pixelKeyboard = composeRule.runOnIdle { activity.simulateKeyboard(keyboardHeight = 374.dp, navBarBottom = 24.dp) }
        openFieldOptions()
        assertPanelAboveKeyboard(pixelKeyboard)
        settleMenuTransition(activity)
        captureScreen("field_options_pixel_keyboard_top")
        for (option in EditorFieldOption.entries) {
            composeRule.onNodeWithText(getResourceString(option.label)).performScrollTo().assertIsDisplayed()
            assertPanelAboveKeyboard(pixelKeyboard)
        }
        captureScreen("field_options_pixel_keyboard_customization_links")
    }

    @Test
    fun fieldOptionsStayAboveChangingKeyboardInsets() {
        val (activity, keyboard) = showEditor()
        hideKeyboard(activity, keyboard)
        openFieldOptions()
        val height = activity.window.decorView.height
        val density = activity.resources.displayMetrics.density
        for (imeHeight in listOf(100, 200, 300, 420, 300, 100, 0, 300)) {
            composeRule.runOnIdle { activity.dispatchInsets(navBarBottom = 48.dp, imeBottom = imeHeight.dp) }
            composeRule.waitForIdle()
            val viewport = composeRule.onNodeWithTag("editor_field_options").fetchSemanticsNode().boundsInRoot
            val availableBottom = height - maxOf(48, imeHeight) * density
            assertTrue(viewport.bottom <= availableBottom + 1, "Options extend below the usable viewport: $viewport / $availableBottom")
            val lastOption = composeRule.onNodeWithText(getResourceString(R.string.compose_editor_field_description))
            lastOption.performScrollTo().assertIsDisplayed()
            assertTrue(lastOption.fetchSemanticsNode().boundsInRoot.bottom <= availableBottom + 1)
        }
        composeRule.runOnIdle { activity.simulateKeyboard() }
        captureScreen("field_options_after_keyboard_reopens")
    }

    @Test
    fun fieldOptionsDoNotResizeWebFields() {
        val (activity, keyboard) = showEditor(tags = listOf("Vocabulary"))
        for (keyboardVisible in listOf(true, false)) {
            if (!keyboardVisible) hideKeyboard(activity, keyboard)
            val before = composeRule.onNodeWithTag("editor_fields").fetchSemanticsNode().boundsInRoot
            openFieldOptions()
            composeRule.onNodeWithText("Japanese").assertIsDisplayed()
            assertEquals(before, composeRule.onNodeWithTag("editor_fields").fetchSemanticsNode().boundsInRoot)
            composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_bold)).assertDoesNotExist()
            composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_close_field_options)).performClick()
            assertEquals(before, composeRule.onNodeWithTag("editor_fields").fetchSemanticsNode().boundsInRoot)
            composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_bold)).assertIsDisplayed()
        }
    }

    @Test
    fun optionsUnpinned() {
        showEditor(pinned = false)
        openFieldOptions()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_pin_field)).assertIsOff().assertIsDisplayed()
        composeRule.waitForIdle()
        captureScreen("options_unpinned")
    }

    @Test
    fun optionsInRtl() {
        RuntimeEnvironment.setQualifiers("+ldrtl")
        openOptions()
        captureScreen("options_rtl")
    }

    @Test
    fun optionsWithLargeTextOnSmallScreen() {
        RuntimeEnvironment.setQualifiers("+w320dp-h640dp")
        RuntimeEnvironment.setFontScale(2f)
        val (activity, keyboard) = showEditor(fieldName = LONG_FIELD_NAME, noteTypeName = LONG_NOTE_TYPE_NAME)
        hideKeyboard(activity, keyboard)
        openFieldOptions()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_pin_field)).assertIsDisplayed()
        composeRule.waitForIdle()
        captureScreen("options_large_text_top")
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_right_to_left)).performScrollTo().assertIsDisplayed()
        composeRule.waitForIdle()
        captureScreen("options_large_text_controls")
        composeRule.onNodeWithText("Japanese").performScrollTo().assertIsDisplayed()
        composeRule.waitForIdle()
        captureScreen("options_large_text_bottom")
    }

    @Test
    fun customizeToolbarFromFieldOptions() {
        val (activity, _) = showEditor(presets = samplePresets())
        openFieldCustomization(R.string.compose_editor_toolbar)
        composeRule
            .onNodeWithText(getResourceString(R.string.compose_editor_preset))
            .assertIsDisplayed()
            .assertTextContains(getResourceString(R.string.compose_editor_default_toolbar))
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_toolbar)).assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(300)
        settleMenuTransition(activity)
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_show_field_toolbar)).assertIsDisplayed()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_bold)).assertIsDisplayed()
        composeRule.onNodeWithText("Manage toolbar presets").assertDoesNotExist()
        captureScreen("customize_toolbar")
    }

    @Test
    fun toolbarPresetOptionsMenuIsOpaque() {
        val (activity, _) = showEditor(presets = samplePresets())
        openFieldCustomization(R.string.compose_editor_toolbar)
        chooseToolbarPreset("Language learning")
        composeRule
            .onNodeWithContentDescription(
                targetContext.getString(R.string.compose_editor_preset_options, "Language learning"),
            ).performClick()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_duplicate_preset)).assertIsDisplayed()
        settleMenuTransition(activity)
        val pixels = composeRule.onNode(isPopup()).captureToImage().toPixelMap()
        val colors = activity.toMaterial3ColorScheme()
        val opaque = colors.surfaceContainer.compositeOver(colors.surface).toArgb()
        assertEquals(opaque, pixels[pixels.width / 2, 2].toArgb(), "The menu background must be opaque")
        captureScreen("toolbar_preset_options")
    }

    @Test
    fun fieldToolbarPresetChoices() {
        showEditor(
            fieldName = "A very long field name for additional information",
            noteTypeName = "A very long shared note type name",
            presets = samplePresets(),
        )
        openFieldCustomization(R.string.compose_editor_toolbar)
        composeRule
            .onNodeWithText(getResourceString(R.string.compose_editor_preset))
            .assertIsDisplayed()
            .assertTextContains(getResourceString(R.string.compose_editor_default_toolbar))
        captureScreen("field_toolbar_presets")
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_preset)).performClick()
        composeRule.onNode(isPopup()).assertIsDisplayed()
        composeRule
            .onNode(
                hasText(getResourceString(R.string.compose_editor_default_toolbar)) and hasAnyAncestor(isPopup()),
            ).assertIsSelected()
        captureScreen("toolbar_preset_choices")
        composeRule.onNode(hasText(samplePresets().last().name) and hasAnyAncestor(isPopup())).performClick()
        composeRule.onNode(isPopup()).assertDoesNotExist()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_preset)).assertTextContains(samplePresets().last().name)
        captureScreen("toolbar_long_preset_selected")
    }

    @Test
    fun customizeToolbarWithLargeText() {
        RuntimeEnvironment.setQualifiers("+w320dp-h640dp")
        RuntimeEnvironment.setFontScale(2f)
        val (activity, keyboard) = showEditor(fieldName = LONG_FIELD_NAME, noteTypeName = LONG_NOTE_TYPE_NAME)
        openFieldCustomization(R.string.compose_editor_toolbar)
        scrollToolbarHeaderTo(activity, R.string.compose_editor_preset)
        assertPanelAboveKeyboard(keyboard)
        captureScreen("customize_toolbar_large_text")
        val presetBounds =
            composeRule
                .onNodeWithText(
                    getResourceString(R.string.compose_editor_preset),
                ).assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInWindow
        val viewport = composeRule.onNodeWithTag("editor_toolbar_list").fetchSemanticsNode().boundsInWindow
        assertTrue(
            presetBounds.bottom <= viewport.bottom,
            "The complete preset selector must fit in the scrolling viewport: $presetBounds inside $viewport",
        )
        scrollToolbarHeaderTo(activity, R.string.compose_editor_show_field_toolbar)
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_show_field_toolbar)).performClick().assertIsOff()
        scrollToolbarHeaderTo(activity, R.string.compose_editor_preset)
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_preset)).assertIsNotEnabled()
        captureScreen("customize_toolbar_large_text_hidden")
        scrollToolbarHeaderTo(activity, R.string.compose_editor_custom_buttons)
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_custom_buttons)).assertIsDisplayed().assertIsNotEnabled()
        assertPanelAboveKeyboard(keyboard)
        captureScreen("customize_toolbar_large_text_custom_buttons")
    }

    @Test
    fun sharedToolbarPresetEditing() {
        val (activity, keyboard) = showEditor(presets = samplePresets())
        openFieldCustomization(R.string.compose_editor_toolbar)
        val dialog = composeRule.onNodeWithTag("editor_field_panel").fetchSemanticsNode().id
        chooseToolbarPreset("Language learning")
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_preset_scope)).assertIsDisplayed()
        assertEquals(
            dialog,
            composeRule.onNodeWithTag("editor_field_panel").fetchSemanticsNode().id,
            "Changing presets must retain the panel",
        )
        settleMenuTransition(activity)
        val shortcut =
            composeRule
                .onNodeWithText(getResourceString(R.string.compose_editor_custom_buttons))
                .fetchSemanticsNode()
                .boundsInRoot
        assertPanelAboveKeyboard(keyboard)
        val panelBounds = composeRule.onNodeWithTag("editor_field_panel").fetchSemanticsNode().boundsInRoot
        assertTrue(shortcut.bottom <= panelBounds.bottom, "The library action must remain inside the panel viewport")
        captureScreen("shared_toolbar_preset_editing")
        composeRule
            .onNode(
                hasContentDescription(getResourceString(R.string.compose_editor_close_field_options)) and
                    hasAnyAncestor(hasTestTag("editor_field_panel")),
            ).performClick()
        composeRule.onNodeWithText(getResourceString(CommonString.discard)).performClick()
        composeRule.onNodeWithTag("editor_field_panel").assertDoesNotExist()
    }

    @Test
    fun toolbarConfigurationOpen() {
        val ruby = EditorCustomToolbarButton(label = "Ruby", prefix = "<ruby>", suffix = "</ruby>")
        val (activity, keyboard) =
            showEditor(
                toolbar =
                    EditorFieldToolbar(
                        customButtons = listOf(ruby),
                        hiddenButtons = EditorToolbarButton.entries.map { it.name }.toSet() - setOf("BOLD", "ITALIC", "UNDERLINE"),
                    ),
            )
        hideKeyboard(activity, keyboard)
        openFieldCustomization(R.string.compose_editor_toolbar)
        composeRule.runOnIdle {
            val list = (activity.window.decorView as ViewGroup).descendants.filterIsInstance<RecyclerView>().first { it.isShown }
            (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(1, 0)
        }
        composeRule.moveToolbarButton("Ruby", targetContext.getString(R.string.compose_editor_move_button_down, "Ruby"))
        val bold = composeRule.onNodeWithText(getResourceString(R.string.compose_editor_bold))
        bold.assertIsSelected()
        // Capture the settled rows without an Android ripple following a moved RecyclerView holder.
        bold.performSemanticsAction(SemanticsActions.OnClick).assertIsNotSelected()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_custom_button)).assertIsDisplayed()
        captureScreen("toolbar_configuration")
        composeRule
            .onNode(hasText("Ruby") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected))
            .performSemanticsAction(SemanticsActions.OnClick)
            .assertIsNotSelected()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_custom_button)).assertIsDisplayed()
        captureScreen("toolbar_configuration_custom_available")
        composeRule
            .onNode(hasContentDescription(getResourceString(CommonString.save)) and hasAnyAncestor(hasTestTag("editor_field_panel")))
            .performClick()
        bold.assertDoesNotExist()
    }

    @Test
    fun customizeToolbarScrolledDown() = captureFloatingCustomButtons()

    @Test
    fun floatingCustomButtonsInRtl() = captureFloatingCustomButtons(rtl = true)

    @Test
    fun floatingCustomButtonsWithLargeText() = captureFloatingCustomButtons(largeText = true)

    private fun captureFloatingCustomButtons(
        rtl: Boolean = false,
        largeText: Boolean = false,
    ) {
        RuntimeEnvironment.setQualifiers("+w427dp-h952dp")
        if (rtl) RuntimeEnvironment.setQualifiers("+ldrtl")
        if (largeText) RuntimeEnvironment.setFontScale(2f)
        val (activity, keyboard) =
            showEditor(
                deckName = "parent deck::child deck",
                focusState = mutableStateOf(EditorToolbarState(activeField = 0)),
                templates = listOf(EditorCustomToolbarButton(id = "ruby", label = "Ruby", prefix = "<ruby>", suffix = "</ruby>")),
            )
        hideKeyboard(activity, keyboard)
        openFieldCustomization(R.string.compose_editor_toolbar)
        val action = composeRule.onNodeWithText(getResourceString(R.string.compose_editor_custom_buttons))
        val shortcut = action.fetchSemanticsNode().boundsInRoot
        val panel = composeRule.onNodeWithTag("editor_field_panel").fetchSemanticsNode().boundsInRoot
        if (!largeText) assertEquals(rtl, shortcut.center.x < panel.center.x, "The action follows the layout's trailing edge")
        composeRule.runOnIdle {
            val list = (activity.window.decorView as ViewGroup).descendants.filterIsInstance<RecyclerView>().first { it.isShown }
            // Match the Pixel: header controls and the Active heading have scrolled out of view.
            (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(10, 0)
        }
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_show_field_toolbar)).assertIsNotDisplayed()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_custom_buttons)).assertIsDisplayed()
        assertEquals(
            shortcut,
            composeRule.onNodeWithText(getResourceString(R.string.compose_editor_custom_buttons)).fetchSemanticsNode().boundsInRoot,
        )
        settleMenuTransition(activity)
        val pixels = action.captureToImage().toPixelMap()
        assertEquals(
            activity.toMaterial3ColorScheme().primaryContainer.toArgb(),
            pixels[pixels.width / 2, 2].toArgb(),
            "The floating library action uses the filled tonal colour",
        )
        val suffix =
            when {
                rtl -> "_rtl"
                largeText -> "_large_text"
                else -> ""
            }
        captureScreen("customize_toolbar_scrolled_down$suffix")
        composeRule.runOnIdle {
            val list = (activity.window.decorView as ViewGroup).descendants.filterIsInstance<RecyclerView>().first { it.isShown }
            list.scrollBy(0, list.computeVerticalScrollRange())
        }
        val lastHandle =
            composeRule
                .onNodeWithContentDescription(targetContext.getString(R.string.compose_editor_reorder_button, "Ruby"))
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        assertTrue(lastHandle.bottom < shortcut.top, "The last drag handle must clear the entire floating action")
        assertEquals(shortcut, action.fetchSemanticsNode().boundsInRoot, "Scrolling to the end must not move the action")
        captureScreen("customize_toolbar_scrolled_to_end$suffix")
    }

    @Test
    fun quickActionsConfigurationOpen() {
        showEditor(toolbar = EditorFieldToolbar(quickActions = listOf("gallery", "pin")))
        openFieldCustomization(R.string.compose_editor_quick_actions)
        composeRule.moveToolbarButton(
            getResourceString(R.string.compose_editor_pin_field),
            targetContext.getString(R.string.compose_editor_move_button_up, getResourceString(R.string.compose_editor_pin_field)),
        )
        composeRule
            .onNode(
                hasText(getResourceString(R.string.compose_editor_pin_field)) and
                    SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
            ).assertIsSelected()
        composeRule.onNodeWithText(getResourceString(CommonString.multimedia_editor_image_field_editing_galery)).assertIsSelected()
        val drawing = composeRule.onNodeWithText(getResourceString(CommonString.drawing))
        drawing.assertIsNotSelected()
        drawing.performClick().assertIsSelected()
        drawing.performClick().assertIsNotSelected()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_quick_actions_help)).assertIsDisplayed()
        captureScreen("quick_actions_configuration")
        composeRule
            .onNode(hasContentDescription(getResourceString(CommonString.save)) and hasAnyAncestor(hasTestTag("editor_field_panel")))
            .performClick()
        drawing.assertDoesNotExist()
    }

    @Test
    fun quickActionsConfigurationWithNoneActive() {
        showEditor(toolbar = EditorFieldToolbar(quickActions = emptyList()))
        openFieldCustomization(R.string.compose_editor_quick_actions)
        composeRule.onNodeWithText(getResourceString(CommonString.user_active_columns)).assertIsDisplayed()
        composeRule.onNodeWithText(getResourceString(CommonString.user_potential_columns)).assertIsDisplayed()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_quick_actions_help)).assertIsDisplayed()
        captureScreen("quick_actions_configuration_none_active")
    }

    @Test
    fun toolbarConfigurationWithNoneActive() {
        showEditor(toolbar = EditorFieldToolbar(hiddenButtons = EditorToolbarButton.entries.map { it.name }.toSet()))
        openFieldCustomization(R.string.compose_editor_toolbar)
        composeRule.onNodeWithText(getResourceString(CommonString.user_active_columns)).assertIsDisplayed()
        composeRule.onNodeWithText(getResourceString(CommonString.user_potential_columns)).assertIsDisplayed()
        captureScreen("toolbar_configuration_none_active")
    }

    @Test
    fun customButtonLibraryOpen() {
        val (activity, _) =
            showEditor(
                templates = listOf(EditorCustomToolbarButton(label = "Ruby", prefix = "<ruby>", suffix = "</ruby>")),
            )
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_options)).performClick()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_custom_buttons)).assertDoesNotExist()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_field_options)).performClick()
        openFieldSection(R.string.compose_editor_toolbar)
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_custom_buttons)).assertIsDisplayed()
        captureScreen("customize_toolbar_custom_buttons")
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_custom_buttons)).performClick()
        composeRule.onNodeWithText("Ruby").assertIsDisplayed()
        settleMenuTransition(activity)
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
    fun scrollToolbarSingleRow() = captureToolbarRows(EditorToolbarDisplay.SCROLL, listOf(3), "toolbar_scroll_single_row")

    @Test
    fun scrollToolbarFilledSingleRow() = captureToolbarRows(EditorToolbarDisplay.SCROLL, listOf(7), "toolbar_scroll_full_row")

    @Test
    fun scrollToolbarDefersOffscreenButtonsUntilScrolled() {
        val actions = mutableListOf<WebEditorAction>()
        showEditor(onAction = actions::add)
        val bold = getResourceString(R.string.compose_editor_bold)
        val equation = getResourceString(R.string.compose_editor_equations)
        // Creating every offscreen button/tooltip competes with WebView and IME startup.
        composeRule.onNodeWithContentDescription(bold).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(equation).assertDoesNotExist()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasContentDescription(equation))
        composeRule.onNodeWithContentDescription(equation).assertIsDisplayed().performClick()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasContentDescription(bold))
        composeRule.onNodeWithContentDescription(bold).assertIsDisplayed().performClick()
        assertEquals(listOf(WebEditorAction.MATHJAX_INLINE, WebEditorAction.BOLD), actions)
    }

    @Test
    fun switchingFieldsShowsTheNewFieldsFirstToolbarButton() {
        val focus = mutableStateOf(EditorToolbarState(hasSelection = true, activeField = 0))
        val equation = getResourceString(R.string.compose_editor_equations)
        showEditor(
            toolbar = EditorFieldToolbar(buttonOrder = listOf(EditorToolbarButton.EQUATIONS.name)),
            focusState = focus,
        )
        composeRule.onNodeWithContentDescription(equation).assertDoesNotExist()
        composeRule
            .onNode(
                hasScrollAction(),
            ).performScrollToNode(hasContentDescription(getResourceString(CommonString.multimedia_editor_popup_audio)))
        composeRule.runOnIdle { focus.value = focus.value.copy(activeField = 1) }
        composeRule.onNodeWithContentDescription(equation).assertIsDisplayed()
    }

    @Test
    fun stackedToolbarModeSwitchKeepsFollowingButtonsInColumns() {
        showEditor(toolbarDisplay = EditorToolbarDisplay.STACK)
        val bounds =
            EditorToolbarButton.entries.filterNot { it.clozeOnly || it == EditorToolbarButton.SOURCE_MODE }.map {
                composeRule.onNodeWithContentDescription(getResourceString(it.label)).fetchSemanticsNode().boundsInRoot
            }
        val bottom = bounds.filter { it.top == bounds.first().top }
        val spacing = bottom[1].center.x - bottom[0].center.x
        bounds.forEach { item ->
            val column = (item.center.x - bottom[0].center.x) / spacing
            assertEquals(column.roundToInt().toFloat(), column, 0.02f, "Every icon must align with a column, including after A/<>")
        }
        captureScreen("toolbar_stack_mode_switch_columns")
    }

    @Test
    fun stackedToolbarSingleRowUsesStartPadding() = captureToolbarRows(EditorToolbarDisplay.STACK, listOf(3), "toolbar_stack_single_row")

    @Test
    fun stackedToolbarFilledSingleRowIsCentered() = captureToolbarRows(EditorToolbarDisplay.STACK, listOf(8), "toolbar_stack_full_row")

    @Test
    fun stackedToolbarFullAndPartialRowsBuildUpwardWithTheSameStartPadding() =
        captureToolbarRows(EditorToolbarDisplay.STACK, listOf(8, 2), "toolbar_stack_full_and_partial_rows")

    @Test
    fun stackedToolbarThreeRowsBuildUpward() =
        captureToolbarRows(EditorToolbarDisplay.STACK, listOf(6, 6, 1), "toolbar_stack_three_rows", columns = 6)

    @Test
    fun stackedToolbarOverflowStartsAtTheBottom() {
        RuntimeEnvironment.setQualifiers("+w320dp")
        showEditor(
            toolbarDisplay = EditorToolbarDisplay.STACK,
            toolbar =
                EditorFieldToolbar(
                    buttonOrder = EditorToolbarButton.entries.map { it.name },
                    customButtons =
                        (1..3).map {
                            EditorCustomToolbarButton(id = "custom-$it", label = "Custom $it", prefix = "<b>", suffix = "</b>")
                        },
                ),
        )
        val bold = composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_bold))
        bold.assertIsDisplayed()
        val last = composeRule.onNodeWithText("Custom 3")
        last.assertIsNotDisplayed()
        captureScreen("toolbar_stack_overflow_bottom")
        last.performScrollTo().assertIsDisplayed()
        bold.assertIsNotDisplayed()
        captureScreen("toolbar_stack_overflow_top")
    }

    private fun captureToolbarRows(
        display: EditorToolbarDisplay,
        rowSizes: List<Int>,
        name: String,
        columns: Int = 8,
    ) {
        // Leave spare width after the buttons so unequal full-row margins cannot go unnoticed.
        RuntimeEnvironment.setQualifiers("+w${if (display == EditorToolbarDisplay.SCROLL) 401 else columns * 48 + 29}dp")
        // Exercise the regular 48dp columns; the wider A/<> switch has separate screenshots.
        val buttons = EditorToolbarButton.entries.filterNot { it.clozeOnly || it == EditorToolbarButton.SOURCE_MODE }.take(rowSizes.sum())
        showEditor(
            toolbarDisplay = display,
            toolbar = EditorFieldToolbar(hiddenButtons = (EditorToolbarButton.entries - buttons.toSet()).map { it.name }.toSet()),
        )
        val rows =
            buttons
                .map {
                    composeRule
                        .onNodeWithContentDescription(
                            getResourceString(it.label),
                        ).assertIsDisplayed()
                        .fetchSemanticsNode()
                        .boundsInRoot
                }.groupBy { it.top.roundToInt() }
                .toSortedMap()
                .values
        val expectedRows = if (display == EditorToolbarDisplay.STACK) rowSizes.reversed() else rowSizes
        assertEquals(expectedRows, rows.map { it.size })
        if (display == EditorToolbarDisplay.STACK) {
            val screenCenter =
                composeRule
                    .onRoot()
                    .fetchSemanticsNode()
                    .boundsInRoot.center.x
            val widestRow = rows.maxBy { it.size }
            val buttonSpacing = widestRow[1].center.x - widestRow[0].center.x
            val firstCenter = screenCenter - (columns - 1) / 2f * buttonSpacing
            rows.forEachIndexed { index, row ->
                assertEquals(firstCenter, row.first().center.x, 1f, "Row $index must use the same start padding")
                assertTrue(row.zipWithNext().all { (before, after) -> before.right <= after.left }, "Keep the configured button order")
                if (row.size == columns) {
                    assertEquals(screenCenter, (row.first().left + row.last().right) / 2f, 1f, "Full row $index must be centered")
                }
            }
        }
        toolbarOverflowButton().assertDoesNotExist()
        captureScreen(name)
    }

    @Test
    fun stackedToolbarWrapsButtonsAboveTheFirstRow() {
        showEditor(toolbarDisplay = EditorToolbarDisplay.STACK)
        val first =
            composeRule
                .onNodeWithContentDescription(
                    getResourceString(R.string.compose_editor_bold),
                ).fetchSemanticsNode()
                .boundsInRoot
        val nextRow =
            composeRule
                .onNodeWithContentDescription(getResourceString(R.string.compose_editor_subscript))
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        assertTrue(nextRow.bottom <= first.top, "Keep Bold in the bottom row and wrap later buttons above it")
        toolbarOverflowButton().assertDoesNotExist()
        captureScreen("toolbar_stacked")
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_options)).performClick()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_field_options)).assertIsDisplayed()
        captureScreen("toolbar_stacked_general_options")
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_field_options)).performClick()
        composeRule.onNodeWithText("Japanese").assertIsDisplayed()
        captureScreen("toolbar_stacked_field_options")
    }

    @Test
    fun visualToolbarLayoutChoices() {
        val (activity, _) = showEditor()
        for (display in EditorToolbarDisplay.entries) {
            composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_options)).performClick()
            composeRule.onNodeWithText(getResourceString(R.string.compose_editor_toolbar_layout)).performClick()
            composeRule.onNodeWithText(getResourceString(R.string.compose_editor_toolbar_layout)).assertIsDisplayed()
            composeRule.onNodeWithText(getResourceString(CommonString.dialog_cancel)).assertDoesNotExist()
            composeRule.onNodeWithText(getResourceString(display.label)).assertIsSelected()
            assertToolbarChoicesAreSideBySide()
            settleMenuTransition(activity)
            captureScreen("toolbar_layout_${display.name.lowercase()}")
            val backgrounds =
                EditorToolbarDisplay.entries.associateWith { layout ->
                    val image = composeRule.onNodeWithText(getResourceString(layout.label)).captureToImage().toPixelMap()
                    // Sample the empty top padding, away from the radio, label and preview.
                    image[image.width / 2, 2]
                }
            for (other in EditorToolbarDisplay.entries - display) {
                composeRule.onNodeWithText(getResourceString(other.label)).assertIsNotSelected()
                assertNotEquals(
                    backgrounds[other],
                    backgrounds[display],
                    "The selected $display choice needs a visible background highlight",
                )
            }
            val next = EditorToolbarDisplay.entries[(display.ordinal + 1) % EditorToolbarDisplay.entries.size]
            composeRule.onNodeWithText(getResourceString(next.label)).performClick()
            composeRule.onNodeWithText(getResourceString(next.label)).assertDoesNotExist()
        }
        toolbarOverflowButton().assertDoesNotExist()
    }

    @Test
    fun toolbarLayoutChoicesOnSmallScreen() {
        RuntimeEnvironment.setQualifiers("+w320dp")
        showEditor()
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_options)).performClick()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_toolbar_layout)).performClick()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_toolbar_scroll)).assertIsSelected()
        assertToolbarChoicesAreSideBySide()
        captureScreen("toolbar_layout_small_screen")
    }

    @Test
    fun toolbarLayoutChoicesOnSmallScreenWithLargeText() {
        RuntimeEnvironment.setQualifiers("+w320dp")
        RuntimeEnvironment.setFontScale(2f)
        showEditor()
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_options)).performClick()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_toolbar_layout)).performClick()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_toolbar_hidden)).performScrollTo().assertIsDisplayed()
        captureScreen("toolbar_layout_large_text")
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_toolbar_scroll)).performScrollTo().assertIsDisplayed()
        captureScreen("toolbar_layout_large_text_start")
    }

    private fun assertToolbarChoicesAreSideBySide() {
        val bounds =
            EditorToolbarDisplay.entries.map { display ->
                composeRule
                    .onNodeWithText(getResourceString(display.label))
                    .assertIsDisplayed()
                    .fetchSemanticsNode()
                    .boundsInRoot
            }
        bounds.zipWithNext().forEach { (left, right) ->
            assertEquals(left.top, right.top, "Keep the choices in one horizontal row")
            assertEquals(left.bottom, right.bottom, "Give each choice the same height")
            assertTrue(left.right < right.left, "Leave a gap between choices")
        }
    }

    @Test
    fun hiddenToolbarKeepsBottomBackgroundUntinted() {
        val (activity, keyboard) = showEditor(toolbarDisplay = EditorToolbarDisplay.HIDDEN, tags = listOf("Vocabulary"))
        activity.enableEdgeToEdge()
        hideKeyboard(activity, keyboard)
        val tag = composeRule.onNodeWithText("Vocabulary").fetchSemanticsNode().boundsInRoot
        // Draw the content view directly, before Android adds its navigation-bar contrast scrim.
        val image = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0).drawToBitmap()
        captureScreen("toolbar_hidden_no_tint")
        assertEquals(
            image[image.width / 2, tag.top.toInt() - 1],
            image[image.width / 2, image.height - 24.dp.toPx(activity)],
            "The bottom inset must match the field and tag background when the toolbar is hidden",
        )
    }

    @Test
    fun hiddenToolbarKeepsFieldOptionsAccessible() {
        showEditor(toolbarDisplay = EditorToolbarDisplay.HIDDEN)
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_bold)).assertDoesNotExist()
        toolbarOverflowButton().assertDoesNotExist()
        captureScreen("toolbar_hidden")
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_options)).performClick()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_field_options)).performClick()
        composeRule.onNodeWithText("Japanese").assertIsDisplayed()
        captureScreen("hidden_toolbar_field_options")
    }

    @Test
    fun allToolbarButtonsHidden() {
        showEditor(toolbar = EditorFieldToolbar(hiddenButtons = EditorToolbarButton.entries.map { it.name }.toSet()))
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_bold)).assertDoesNotExist()
        captureScreen("all_toolbar_buttons_hidden")
    }

    @Test
    fun overflowingToolbarPeeksWhenTheEdgeFallsBetweenIcons() {
        RuntimeEnvironment.setQualifiers("+w400dp")
        showEditor()
        assertToolbarHasVisibleClippedContent()
        captureScreen("toolbar_overflow_peek")
    }

    @Test
    fun overflowingToolbarWithCustomButtonsPeeks() {
        RuntimeEnvironment.setQualifiers("+w400dp")
        showEditor(
            toolbar =
                EditorFieldToolbar(
                    customButtons =
                        listOf(
                            EditorCustomToolbarButton(id = "ruby", label = "Ruby", prefix = "<ruby>", suffix = "</ruby>"),
                            EditorCustomToolbarButton(id = "long", label = "A long custom button", prefix = "<b>", suffix = "</b>"),
                        ),
                ),
        )
        assertToolbarHasVisibleClippedContent()
        captureScreen("toolbar_custom_overflow_peek")
    }

    @Test
    fun overflowingToolbarPeeksInRtl() {
        RuntimeEnvironment.setQualifiers("+ldrtl-w400dp")
        showEditor()
        assertToolbarHasVisibleClippedContent(rtl = true)
        captureScreen("toolbar_overflow_peek_rtl")
    }

    @Test
    fun toolbarKeepsPixelSpacingWhenAnIconAlreadyPeeks() {
        RuntimeEnvironment.setQualifiers("w426dp-h952dp-mdpi")
        showEditor()
        assertToolbarHasVisibleClippedContent()
        val bold =
            composeRule
                .onNodeWithContentDescription(
                    getResourceString(R.string.compose_editor_bold),
                ).fetchSemanticsNode()
                .boundsInRoot
        val italic =
            composeRule
                .onNodeWithContentDescription(
                    getResourceString(R.string.compose_editor_italic),
                ).fetchSemanticsNode()
                .boundsInRoot
        assertEquals(12f, bold.left, 1f)
        assertEquals(48f, italic.left - bold.left, 1f, "Keep the Pixel's existing spacing")
        captureScreen("toolbar_pixel_spacing_without_fade")
    }

    private fun assertToolbarHasVisibleClippedContent(rtl: Boolean = false) {
        val toolbar = composeRule.onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot
        val axis = composeRule.onNode(hasScrollAction()).fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange]
        assertTrue(axis.maxValue() > 0, "This case must overflow")
        val icons =
            EditorToolbarButton.entries.filterNot { it.clozeOnly }.flatMap { button ->
                composeRule.onAllNodes(hasContentDescription(getResourceString(button.label)), useUnmergedTree = true).fetchSemanticsNodes()
            }
        assertTrue(
            icons.any {
                val inset =
                    if (it.config.contains(SemanticsProperties.Role) &&
                        it.config[SemanticsProperties.Role] == Role.Switch
                    ) {
                        2 * targetContext.resources.displayMetrics.density
                    } else {
                        0f
                    }
                val left = it.positionInRoot.x + inset
                val right = it.positionInRoot.x + it.size.width - inset
                if (rtl) left < toolbar.left && right > toolbar.left else left < toolbar.right && right > toolbar.right
            },
            "An overflowing initial toolbar must visibly cut through an icon, not just its touch padding",
        )
    }

    @Test
    fun toolbarClippedEdge() {
        captureToolbarClippedEdge(rtl = false)
    }

    @Test
    fun toolbarClippedEdgeRtl() {
        captureToolbarClippedEdge(rtl = true)
    }

    private fun captureToolbarClippedEdge(rtl: Boolean) {
        // Place the edge through an icon, rather than through the gap between buttons.
        RuntimeEnvironment.setQualifiers("+${if (rtl) "ldrtl" else "ldltr"}-w420dp")
        showEditor()
        val bold =
            composeRule
                .onNodeWithContentDescription(
                    getResourceString(R.string.compose_editor_bold),
                ).fetchSemanticsNode()
                .boundsInRoot
        val viewport = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals(rtl, bold.center.x > viewport.center.x)
        toolbarOverflowButton().assertDoesNotExist()
        val name = if (rtl) "toolbar_clipped_edge_rtl" else "toolbar_clipped_edge"
        captureScreen(name)
        val toolbar = composeRule.onNode(hasScrollAction())
        toolbar.performSemanticsAction(SemanticsActions.ScrollBy) { it(10_000f, 0f) }
        val axis = toolbar.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange]
        assertEquals(axis.maxValue(), axis.value())
        captureScreen("${name}_fully_scrolled")
        openFieldOptions()
        composeRule.onNodeWithText("Japanese").assertIsDisplayed()
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
        composeRule.onNodeWithContentDescription(title).assertIsNotDisplayed()
    }

    @Test
    fun fieldHelpDescribesEveryVisibleOption() {
        openOptions()
        // Check the actual controls too, so adding one without registering its help fails this test.
        val controls =
            composeRule
                .onAllNodes(
                    hasClickAction() and hasAnyAncestor(hasTestTag("editor_field_options")) and
                        !hasContentDescription(getResourceString(R.string.compose_editor_field_options_help)) and
                        !hasContentDescription(getResourceString(R.string.compose_editor_close_field_options)),
                ).fetchSemanticsNodes()
        val labels = controls.map { it.config[SemanticsProperties.Text].first().text }
        assertEquals(EditorFieldOption.entries.size, labels.size)
        assertEquals(EditorFieldOption.entries.map { getResourceString(it.label) }.toSet(), labels.toSet())

        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_field_options_help)).performClick()
        for (option in EditorFieldOption.entries) {
            composeRule.onNode(hasText(getResourceString(option.label)) and hasAnyAncestor(isPopup())).performScrollTo().assertIsDisplayed()
            val help = getResourceString(option.help)
            assertTrue(help.isNotBlank())
            composeRule.onNode(hasText(help) and hasAnyAncestor(isPopup())).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun helpOpen() {
        val (activity, _) = openOptions()
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_field_options_help)).performClick()
        composeRule
            .onNodeWithText(
                targetContext.getString(R.string.compose_editor_field_options_help_scope, "Back", "Basic"),
            ).assertIsDisplayed()
        settleMenuTransition(activity)
        captureScreen("help_open")
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_right_to_left_help)).performScrollTo().assertIsDisplayed()
        settleMenuTransition(activity)
        captureScreen("help_open_field_defaults")
    }

    @Test
    fun helpOpenWithoutKeyboard() {
        val (activity, keyboard) = showEditor()
        hideKeyboard(activity, keyboard)
        openFieldOptions()
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_field_options_help)).performClick()
        composeRule.onNode(isPopup()).assertIsDisplayed()
        settleMenuTransition(activity)
        captureScreen("help_open_without_keyboard")
    }

    @Test
    fun helpOpenRtl() {
        RuntimeEnvironment.setQualifiers("+ldrtl")
        val (activity, _) = openOptions()
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_field_options_help)).performClick()
        settleMenuTransition(activity)
        captureScreen("help_open_rtl")
    }

    @Test
    fun fieldHelpWithLargeTextOnSmallScreen() {
        RuntimeEnvironment.setQualifiers("+w320dp-h640dp")
        RuntimeEnvironment.setFontScale(1.5f)
        val (activity, keyboard) = showEditor()
        hideKeyboard(activity, keyboard)
        openFieldOptions()
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_field_options_help)).performClick()
        composeRule.onNode(isPopup()).assertIsDisplayed()
        settleMenuTransition(activity)
        captureScreen("help_open_small_screen_large_text")
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_right_to_left_help)).performScrollTo().assertIsDisplayed()
        captureScreen("help_open_field_defaults_small_screen_large_text")
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
    fun noteTypePickerUsesNativeRadioChoices() {
        var selected: Long? = null
        showEditor(
            noteTypes = listOf(EditorChoice(1, "Basic"), EditorChoice(2, "Cloze"), EditorChoice(3, LONG_NOTE_TYPE_NAME)),
            onType = { selected = it },
        )
        composeRule.onNodeWithText("Basic").performClick()
        composeRule.waitForIdle()
        val picker = assertNotNull(ShadowDialog.getLatestDialog() as? AlertDialog)
        assertEquals(0, picker.listView.checkedItemPosition)
        captureScreen("note_type_picker")
        val standardRow =
            LayoutInflater.from(picker.context).inflate(
                com.google.android.material.R.layout.mtrl_alert_select_dialog_singlechoice,
                picker.listView,
                false,
            )
        val firstRow = picker.listView.getChildAt(0)
        assertEquals(standardRow.paddingStart, firstRow.paddingStart, "Use Material dialog list insets, not spinner insets")
        assertEquals(standardRow.paddingEnd, firstRow.paddingEnd, "Leave the standard dialog margin after each choice")
        composeRule.runOnIdle { picker.listView.performItemClick(picker.listView.getChildAt(1), 1, 1) }
        composeRule.waitForIdle()
        assertEquals(2L, selected)
        assertTrue(!picker.isShowing)
        // Cancelling does not change the type and the picker can be reopened.
        composeRule.onNodeWithText("Basic").performClick()
        composeRule.waitForIdle()
        val reopened = assertNotNull(ShadowDialog.getLatestDialog() as? AlertDialog)
        composeRule.runOnIdle { reopened.cancel() }
        composeRule.waitForIdle()
        assertEquals(2L, selected)
        assertTrue(!reopened.isShowing)
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
    fun nestedDeckUsesAnkiSeparators() {
        val deckName = "Languages::Japanese::Everyday conversation"
        val (activity, keyboard) = showEditor(deckName = deckName)
        hideKeyboard(activity, keyboard)
        composeRule.onNodeWithText(deckName).assertIsDisplayed()
        captureScreen("nested_deck_name")
    }

    @Test
    fun metadataUsesTheEditorPaneWidthWhenTabletPreviewIsToggled() {
        setTabletQualifiers()
        RuntimeEnvironment.setQualifiers("+w1000dp-h800dp")
        val deckName = "Languages::Japanese::Everyday conversation"
        val (activity, keyboard) = showEditor(deckName = deckName)
        hideKeyboard(activity, keyboard)

        fun bounds(text: String) = composeRule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

        assertTrue(bounds(deckName).bottom < bounds("Basic").top)
        captureScreen("metadata_tablet_preview")
        composeRule.onNodeWithContentDescription(getResourceString(CommonString.note_editor_hide_previewer)).performClick()
        assertTrue(bounds(deckName).right < bounds("Basic").left)
        captureScreen("metadata_tablet_wide")
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
    fun toolbarRichTextMode() {
        showEditor(toolbar = EditorFieldToolbar(buttonOrder = listOf("SOURCE_MODE")))
        captureScreen("toolbar_rich_text_mode")
    }

    @Test
    fun toolbarHtmlMode() {
        showEditor(toolbar = EditorFieldToolbar(buttonOrder = listOf("SOURCE_MODE")), sourceMode = true)
        captureScreen("toolbar_html_mode")
    }

    @Test
    fun toolbarColorIndicators() = captureToolbarColorIndicators("toolbar_color_indicators", "#e91e63", "#ffc107")

    @Test
    fun blackAndWhiteToolbarColorIndicators() = captureToolbarColorIndicators("toolbar_color_black_and_white", "#000000", "#ffffff")

    private fun captureToolbarColorIndicators(
        name: String,
        textColor: String,
        highlightColor: String,
    ) {
        showEditor(
            toolbar = EditorFieldToolbar(buttonOrder = listOf("TEXT_COLOR", "HIGHLIGHT_COLOR")),
            textColor = textColor,
            highlightColor = highlightColor,
        )
        for ((label, color) in listOf(
            R.string.compose_editor_text_color to textColor,
            R.string.compose_editor_highlight_color to highlightColor,
        )) {
            composeRule
                .onNodeWithContentDescription(getResourceString(label))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, color))
            val pixels =
                composeRule
                    .onNodeWithContentDescription(
                        getResourceString(label),
                        useUnmergedTree = true,
                    ).captureToImage()
                    .toPixelMap()
            assertEquals(android.graphics.Color.parseColor(color), pixels[pixels.width / 2, pixels.height - 3].toArgb())
        }
        captureScreen(name)
    }

    @Test
    fun scrollingHidesHeaderAndKeepsSaveInPlace() {
        val visibility = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
        var saves = 0
        showEditor(headerVisibility = visibility, onSave = { saves++ })
        val save = composeRule.onNodeWithContentDescription(getResourceString(CommonString.save))
        val saveBounds = save.fetchSemanticsNode().boundsInRoot
        val fields = composeRule.onNodeWithTag("editor_fields")
        val originalBounds = fields.fetchSemanticsNode().boundsInRoot
        composeRule.runOnIdle { visibility.tryEmit(false) }
        composeRule.onNodeWithText("Default").assertDoesNotExist()
        composeRule.onNodeWithText("Basic").assertDoesNotExist()
        composeRule.onNodeWithText(getResourceString(CommonString.menu_add_note)).assertDoesNotExist()
        val expandedBounds = fields.fetchSemanticsNode().boundsInRoot
        assertTrue(expandedBounds.top < originalBounds.top, "The fields must gain the header space")
        assertEquals(originalBounds.bottom, expandedBounds.bottom, "Keep the toolbar above the keyboard")
        assertEquals(saveBounds, save.fetchSemanticsNode().boundsInRoot, "Save must not move during collapse")
        save.performClick()
        assertEquals(1, saves)
        captureScreen("header_hidden_keyboard")
        composeRule.runOnIdle { visibility.tryEmit(true) }
        composeRule.onNodeWithText("Default").assertIsDisplayed()
        composeRule.onNodeWithText("Basic").assertIsDisplayed()
        assertEquals(originalBounds, fields.fetchSemanticsNode().boundsInRoot)
        assertEquals(saveBounds, save.fetchSemanticsNode().boundsInRoot)
        captureScreen("header_revealed_keyboard")
    }

    @Test
    fun collapsedHeaderWithoutKeyboard() {
        val visibility = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
        val (activity, keyboard) = showEditor(headerVisibility = visibility, tags = listOf("Japanese"))
        hideKeyboard(activity, keyboard)
        composeRule.runOnIdle { visibility.tryEmit(false) }
        composeRule.onNodeWithText("Default").assertDoesNotExist()
        composeRule.onNodeWithText("Japanese").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(getResourceString(CommonString.save)).assertIsDisplayed()
        captureScreen("header_hidden_no_keyboard")
    }

    @Test
    fun filledSaveButton() {
        var saves = 0
        showEditor(onSave = { saves++ })
        captureScreen("filled_save_button")
        composeRule.onNodeWithContentDescription(getResourceString(CommonString.save)).performClick()
        assertEquals(1, saves)
    }

    @Test
    fun saveButtonLongPress() = captureToolbarLongPress(CommonString.save, "save_button_long_press")

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
        openFieldOptions()
        composeRule.onNodeWithText("Japanese").assertIsDisplayed()
        composeRule.waitForIdle()
        return editor
    }

    private fun settleMenuTransition(activity: AnkiActivity) {
        // The options icon uses an Android ripple, outside Compose's animation clock.
        composeRule.runOnIdle { activity.window.decorView.jumpDrawablesToCurrentState() }
        composeRule.waitForIdle()
    }

    private fun mainMenuButton() = composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_options))

    private fun toolbarOverflowButton() =
        composeRule.onNodeWithContentDescription(getResourceString(androidx.appcompat.R.string.abc_action_menu_overflow_description))

    private fun openFieldCustomization(label: Int) {
        openFieldOptions()
        openFieldSection(label)
    }

    private fun openFieldSection(label: Int) {
        composeRule.onNodeWithText(getResourceString(label)).performScrollTo().performClick()
        // Robolectric does not draw automatically. The newly attached RecyclerView header
        // needs a Compose draw pass after the sheet transition to finish its pending layout.
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.runOnUiThread {
            ShadowLooper.idleMainLooper()
            editorActivity.window.decorView.drawToBitmap()
        }
    }

    private fun openFieldOptions() {
        mainMenuButton().performClick()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_field_options)).performClick()
    }

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

    private fun samplePresets() =
        listOf(
            EditorToolbarPreset(id = EditorToolbarPreset.DEFAULT_ID, name = ""),
            EditorToolbarPreset(name = "Language learning"),
            EditorToolbarPreset(name = "Science with equations and additional formatting for long notes"),
        )

    private fun showEditor(
        fieldCount: Int = 2,
        onPanelSaved: (EditorFieldPanelDraft) -> Unit = {},
        fieldName: String = "Back",
        noteTypeName: String = "Basic",
        deckName: String = "Default",
        formats: Set<WebEditorAction> = emptySet(),
        sourceMode: Boolean = false,
        toolbar: EditorFieldToolbar = EditorFieldToolbar(),
        templates: List<EditorCustomToolbarButton> = toolbar.customButtons,
        tags: List<String> = emptyList(),
        onSave: () -> Unit = {},
        onTags: () -> Unit = {},
        onRemoveTag: (String) -> Unit = {},
        noteTypes: List<EditorChoice> = emptyList(),
        onType: (Long) -> Unit = {},
        onAction: (WebEditorAction) -> Unit = {},
        snackbarHost: SnackbarHostState = SnackbarHostState(),
        toolbarDisplay: EditorToolbarDisplay = EditorToolbarDisplay.SCROLL,
        textColor: String = "#000000",
        highlightColor: String = "#ffff00",
        headerVisibility: Flow<Boolean> = emptyFlow(),
        onFieldOptionsVisibility: (Boolean) -> Unit = {},
        onFieldSelected: (EditorFieldTarget) -> Unit = {},
        focusState: State<EditorToolbarState>? = null,
        description: String = "",
        onDescription: (EditorFieldTarget, String) -> Unit = { _, _ -> },
        pinned: Boolean = true,
        rtl: Boolean = false,
        onDirection: (EditorFieldTarget, Boolean) -> Unit = { _, _ -> },
        htmlDefaultState: State<Boolean>? = null,
        onHtmlByDefault: (EditorFieldTarget, Boolean) -> Unit = { _, _ -> },
        collapsedState: State<Boolean>? = null,
        onCollapseByDefault: (EditorFieldTarget, Boolean) -> Unit = { _, _ -> },
        presets: List<EditorToolbarPreset>? = null,
        fieldToolbarState: State<EditorFieldToolbar>? = null,
        presetSelection: State<String>? = null,
        onToolbarVisibility: (EditorFieldTarget, Boolean) -> Unit = { _, _ -> },
        onPreset: (EditorFieldTarget, String) -> Unit = { _, _ -> },
    ): Pair<AnkiActivity, View> {
        val activity = startActivityNormallyOpenCollectionWithIntent(AnkiActivity::class.java, Intent())
        editorActivity = activity
        activity.setContent {
            AnkiDroidTheme {
                val display = remember { mutableStateOf(toolbarDisplay) }
                val currentTemplates = remember { mutableStateOf(templates) }
                val currentPresets =
                    remember {
                        mutableStateOf(
                            presets ?: listOf(EditorToolbarPreset(id = EditorToolbarPreset.DEFAULT_ID, name = "").withToolbar(toolbar)),
                        )
                    }
                val currentToolbar = remember { mutableStateOf(toolbar) }
                val currentPreset = remember { mutableStateOf(EditorToolbarPreset.DEFAULT_ID) }
                NoteEditorScreen(
                    state =
                        editorState(fieldName, noteTypeName, deckName).let { state ->
                            state.copy(
                                tags = tags,
                                toolbarDisplay = display.value,
                                toolbarTextColor = textColor,
                                toolbarHighlightColor = highlightColor,
                                customButtonTemplates = currentTemplates.value,
                                toolbarPresets = currentPresets.value,
                                fields =
                                    List(fieldCount) { index ->
                                        state.fields.getOrNull(index)
                                            ?: state.fields.first().copy(name = "Field ${index + 1}")
                                    }.mapIndexed { index, field ->
                                        if (index ==
                                            1
                                        ) {
                                            field.copy(
                                                toolbar =
                                                    currentPresets.value
                                                        .first {
                                                            it.id ==
                                                                (presetSelection?.value ?: currentPreset.value)
                                                        }.applyTo(
                                                            fieldToolbarState?.value ?: currentToolbar.value,
                                                            currentTemplates.value,
                                                        ),
                                                description = description,
                                                sticky = pinned,
                                                rtl = rtl,
                                                sourceMode = htmlDefaultState?.value ?: false,
                                                collapsed = collapsedState?.value ?: false,
                                                toolbarPresetId =
                                                    presetSelection?.value ?: currentPreset.value,
                                            )
                                        } else {
                                            field
                                        }
                                    },
                            )
                        },
                    toolbarState =
                        focusState ?: remember {
                            mutableStateOf(
                                EditorToolbarState(hasSelection = true, activeField = 1, activeFormats = formats, sourceMode = sourceMode),
                            )
                        },
                    ready = true,
                    busy = false,
                    error = null,
                    snackbar = snackbarHost,
                    onBack = {},
                    onSave = onSave,
                    loadNoteTypes = { noteTypes },
                    onDeck = {},
                    onType = onType,
                    onEditType = {},
                    onTags = onTags,
                    onRemoveTag = onRemoveTag,
                    onPinned = { _, _ -> },
                    onLanguageHint = {},
                    onFieldDescription = onDescription,
                    onFieldDirection = onDirection,
                    onFieldHtmlByDefault = onHtmlByDefault,
                    onFieldCollapseByDefault = onCollapseByDefault,
                    onFieldPanelSave = { draft, done ->
                        onPanelSaved(draft)
                        currentPresets.value = draft.presets
                        draft.changedFields()[1]?.let { settings ->
                            currentPreset.value = settings.presetId
                            currentToolbar.value =
                                currentToolbar.value.copy(showToolbar = settings.showToolbar, quickActions = settings.quickActions)
                            val target = draft.target.copy(field = 1)
                            onToolbarVisibility(target, settings.showToolbar)
                            onPreset(target, settings.presetId)
                        }
                        done()
                    },
                    onAction = onAction,
                    onMedia = {},
                    onAttachMedia = {},
                    onLegacy = {},
                    onPreviewVisibility = {},
                    onToolbarDisplay = { display.value = it },
                    onCustomButtonTemplates = { currentTemplates.value = it },
                    headerVisibility = headerVisibility,
                    onFieldOptionsVisibility = onFieldOptionsVisibility,
                    onFieldSelected = onFieldSelected,
                    editor = { Box(it.testTag("editor_fields")) },
                    preview = { Box(it) },
                )
            }
        }
        composeRule.waitForIdle()
        // Newly attached native lists request real insets, which Robolectric reports as zero.
        // Retain the latest injected keyboard AND system bars when navigating between sections.
        var simulatedInsets: WindowInsetsCompat? = null
        ViewCompat.setOnApplyWindowInsetsListener(activity.window.decorView) { _, insets ->
            if (insets.getInsets(WindowInsetsCompat.Type.statusBars()).top > 0) {
                simulatedInsets = insets
                insets
            } else {
                simulatedInsets ?: insets
            }
        }
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
