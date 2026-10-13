// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.get
import androidx.core.view.drawToBitmap
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CommonString
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
        fieldOptionsButton().performClick()
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
        for (label in listOf(
            R.string.image_occlusion,
            R.string.compose_editor_shrink_images,
            R.string.compose_editor_mathjax_preview,
            R.string.compose_editor_auto_close_tags,
        )) {
            composeRule.onNodeWithText(getResourceString(label)).assertDoesNotExist()
        }
        composeRule.onNodeWithText(getResourceString(CommonString.menu_font_size)).assertIsDisplayed()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_toolbar)).assertIsDisplayed()
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
        val bold = composeRule.onNodeWithText(getResourceString(R.string.compose_editor_bold))
        bold.assertIsSelected()
        bold.performClick().assertIsNotSelected()
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
                    SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
            ).assertIsSelected()
        composeRule.onNodeWithText(getResourceString(R.string.multimedia_editor_image_field_editing_galery)).assertIsSelected()
        val drawing = composeRule.onNodeWithText(getResourceString(R.string.drawing))
        drawing.assertIsNotSelected()
        drawing.performClick().assertIsSelected()
        drawing.performClick().assertIsNotSelected()
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
    fun scrollToolbarSingleRow() = captureToolbarRows(EditorToolbarDisplay.SCROLL, listOf(3), "toolbar_scroll_single_row")

    @Test
    fun scrollToolbarFilledSingleRow() = captureToolbarRows(EditorToolbarDisplay.SCROLL, listOf(7), "toolbar_scroll_full_row")

    @Test
    fun stackedToolbarSingleRowUsesStartPadding() = captureToolbarRows(EditorToolbarDisplay.STACK, listOf(3), "toolbar_stack_single_row")

    @Test
    fun stackedToolbarFilledSingleRowIsCentered() = captureToolbarRows(EditorToolbarDisplay.STACK, listOf(8), "toolbar_stack_full_row")

    @Test
    fun stackedToolbarFullAndPartialRowsBuildUpwardWithTheSameStartPadding() =
        captureToolbarRows(EditorToolbarDisplay.STACK, listOf(8, 2), "toolbar_stack_full_and_partial_rows")

    @Test
    fun stackedToolbarThreeRowsBuildUpward() = captureToolbarRows(EditorToolbarDisplay.STACK, listOf(8, 8, 1), "toolbar_stack_three_rows")

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
    ) {
        // Leave spare width after eight buttons so unequal full-row margins cannot go unnoticed.
        RuntimeEnvironment.setQualifiers("+w${if (display == EditorToolbarDisplay.SCROLL) 401 else 413}dp")
        val buttons = EditorToolbarButton.entries.filterNot { it.clozeOnly }.take(rowSizes.sum())
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
            val firstCenter = screenCenter - 3.5f * buttonSpacing
            rows.forEachIndexed { index, row ->
                assertEquals(firstCenter, row.first().center.x, 1f, "Row $index must use the same start padding")
                assertTrue(row.zipWithNext().all { (before, after) -> before.right <= after.left }, "Keep the configured button order")
                if (row.size == 8) {
                    assertEquals(screenCenter, (row.first().left + row.last().right) / 2f, 1f, "Full row $index must be centered")
                }
            }
            fieldOptionsButton().assertDoesNotExist()
        } else {
            fieldOptionsButton().assertIsDisplayed()
        }
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
        fieldOptionsButton().assertDoesNotExist()
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
        showEditor()
        for (display in EditorToolbarDisplay.entries) {
            composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_options)).performClick()
            composeRule.onNodeWithText(getResourceString(R.string.compose_editor_toolbar)).performClick()
            composeRule.onNodeWithText(getResourceString(display.label)).assertIsSelected()
            captureScreen("toolbar_layout_${display.name.lowercase()}")
            val next = EditorToolbarDisplay.entries[(display.ordinal + 1) % EditorToolbarDisplay.entries.size]
            composeRule.onNodeWithText(getResourceString(next.label)).performClick()
            composeRule.onNodeWithText(getResourceString(next.label)).assertDoesNotExist()
        }
        fieldOptionsButton().assertIsDisplayed()
    }

    @Test
    fun toolbarLayoutChoicesOnSmallScreen() {
        RuntimeEnvironment.setQualifiers("+w320dp")
        showEditor()
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_options)).performClick()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_toolbar)).performClick()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_toolbar_scroll)).assertIsSelected()
        captureScreen("toolbar_layout_small_screen")
    }

    @Test
    fun toolbarLayoutChoicesOnSmallScreenWithLargeText() {
        RuntimeEnvironment.setQualifiers("+w320dp")
        RuntimeEnvironment.setFontScale(2f)
        showEditor()
        composeRule.onNodeWithContentDescription(getResourceString(R.string.compose_editor_options)).performClick()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_toolbar)).performClick()
        composeRule.onNodeWithText(getResourceString(R.string.compose_editor_toolbar_hidden)).performScrollTo().assertIsDisplayed()
        captureScreen("toolbar_layout_large_text")
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
        fieldOptionsButton().assertDoesNotExist()
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
    fun toolbarEndFade() {
        captureToolbarEndFade(rtl = false)
    }

    @Test
    fun toolbarEndFadeRtl() {
        captureToolbarEndFade(rtl = true)
    }

    private fun captureToolbarEndFade(rtl: Boolean) {
        // Place the edge through an icon, rather than through the gap between buttons.
        RuntimeEnvironment.setQualifiers("+${if (rtl) "ldrtl" else "ldltr"}-w420dp")
        showEditor()
        val bold =
            composeRule
                .onNodeWithContentDescription(
                    getResourceString(R.string.compose_editor_bold),
                ).fetchSemanticsNode()
                .boundsInRoot
        val options = fieldOptionsButton().fetchSemanticsNode().boundsInRoot
        assertEquals(rtl, bold.center.x > options.center.x)
        val name = if (rtl) "toolbar_end_fade_rtl" else "toolbar_end_fade"
        captureScreen(name)
        val toolbar = composeRule.onNode(hasScrollAction())
        toolbar.performSemanticsAction(SemanticsActions.ScrollBy) { it(10_000f, 0f) }
        val axis = toolbar.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange]
        assertEquals(axis.maxValue(), axis.value())
        captureScreen("${name}_fully_scrolled")
        fieldOptionsButton().performClick()
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
        toolbarDisplay: EditorToolbarDisplay = EditorToolbarDisplay.SCROLL,
    ): Pair<AnkiActivity, View> {
        val activity = startActivityNormallyOpenCollectionWithIntent(AnkiActivity::class.java, Intent())
        activity.setContent {
            AnkiDroidTheme {
                val display = remember { mutableStateOf(toolbarDisplay) }
                NoteEditorScreen(
                    state =
                        editorState(fieldName, noteTypeName, deckName).let { state ->
                            state.copy(
                                tags = tags,
                                toolbarDisplay = display.value,
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
                    onAttachMedia = {},
                    onLegacy = {},
                    onPreviewVisibility = {},
                    onToolbarDisplay = { display.value = it },
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
