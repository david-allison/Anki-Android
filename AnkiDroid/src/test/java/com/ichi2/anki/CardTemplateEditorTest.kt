// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 Mike Hardy <mike@mikehardy.net>

package com.ichi2.anki

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CardTemplateEditor.CardTemplateFragment
import com.ichi2.anki.CardTemplateEditor.CardTemplateFragment.CardTemplate
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.common.utils.ext.deepClonedInto
import com.ichi2.anki.dialogs.InsertFieldDialog
import com.ichi2.anki.dialogs.InsertFieldDialogViewModel
import com.ichi2.anki.libanki.CardOrdinal
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.libanki.Notetypes
import com.ichi2.anki.libanki.testutils.ext.addNote
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.notetype.ManageNoteTypesState.CardEditor
import com.ichi2.anki.previewer.CardViewerActivity
import com.ichi2.anki.previewer.TemplatePreviewerFragment
import com.ichi2.anki.scheduling.selectTab
import com.ichi2.testutils.assertFalse
import com.ichi2.testutils.dispatchInsets
import com.ichi2.testutils.withSplitPaneUi
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.json.JSONObject
import org.junit.Assume.assumeThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowActivity
import kotlin.test.junit5.JUnit5Asserter.assertEquals
import kotlin.test.junit5.JUnit5Asserter.assertNotEquals
import kotlin.test.junit5.JUnit5Asserter.assertNotNull
import kotlin.test.junit5.JUnit5Asserter.assertNull
import kotlin.test.junit5.JUnit5Asserter.assertTrue

@RunWith(AndroidJUnit4::class)
class CardTemplateEditorTest : RobolectricTest() {
    @Test
    fun `field insertion previews the original card after an unsaved deletion`() {
        val note = addBasicAndReversedNote()
        val reverseCardId = note.cards().single { it.ord == 1 }.id
        val intent = CardTemplateEditor.getIntent(targetContext, col.notetypes.basicAndReversed.id, note.id)
        val editor = startActivityNormallyOpenCollectionWithIntent(CardTemplateEditor::class.java, intent)

        with(editor) {
            deleteSelectedCardType()
            currentFragment!!.showInsertFieldDialog()
            advanceRobolectricLooper()
            val dialog = supportFragmentManager.fragments.filterIsInstance<InsertFieldDialog>().single()
            val metadata = ViewModelProvider(dialog)[InsertFieldDialogViewModel::class.java].metadata

            assertEquals("The preview should use the surviving card", reverseCardId, metadata.cardId)
        }
    }

    @Test
    fun `deletion confirmation counts the original card type after an unsaved deletion`() {
        val noteType = col.notetypes.basicAndReversed
        val spare =
            Notetypes.newTemplate("Spare").apply {
                qfmt = "{{Front}} spare"
                afmt = "{{Back}}"
            }
        col.notetypes.addTemplate(noteType, spare)
        addBasicAndReversedNote("one", "back")
        addBasicAndReversedNote("two", "")

        withCardTemplateEditor(noteType) {
            deleteSelectedCardType()
            selectTab(0)
            advanceRobolectricLooper()
            assertTrue("Unable to delete the card type", shadowOf(this).clickMenuItem(R.id.action_delete))
            advanceRobolectricLooper()

            assertEquals(
                "The remaining reverse card type has one card in the database",
                getQuantityString(CommonPlurals.card_template_editor_confirm_delete, 1, 1, "Card 2"),
                getAlertDialogText(true),
            )
        }
    }

    @Test
    fun `change detection does not serialize the note type - Issue 21912`() {
        val original = col.notetypes.basic
        val edited =
            NotetypeJson(
                original.jsonObject.deepClonedInto(
                    object : JSONObject() {
                        override fun toString(): String = error("Change detection must not serialize the whole note type")
                    },
                ),
            )
        val intent = CardEditor(ntid = original.id).toIntent(targetContext)
        val controller = Robolectric.buildActivity(CardTemplateEditor::class.java, intent)
        saveControllerForCleanup(controller)
        // Guard the note type before onCreate: the initial Back callback update triggers the crash.
        controller.get().tempNoteType = CardTemplateNotetype(edited)
        controller.setup()

        with(controller.get()) {
            assertFalse("Opening the editor should not mark the note type as changed", noteTypeHasChanged())
            edited.templates[0].qfmt += "edited"
            assertTrue("A nested template edit should be detected", noteTypeHasChanged())
            edited.templates[0].qfmt = original.templates[0].qfmt
            assertFalse("Reverting the edit should clear the change", noteTypeHasChanged())
        }
    }

    @Test
    @Throws(Exception::class)
    fun testEditTemplateContents() {
        val noteTypeName = "Basic"

        // Start the CardTemplateEditor with a specific note type, and make sure the note type starts unchanged
        val collectionBasicNoteTypeOriginal = getCurrentDatabaseNoteTypeCopy(noteTypeName)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra("noteTypeId", collectionBasicNoteTypeOriginal.id)
        var templateEditorController =
            Robolectric
                .buildActivity(CardTemplateEditor::class.java, intent)
                .create()
                .start()
                .resume()
                .visible()
        saveControllerForCleanup(templateEditorController)
        var testEditor = templateEditorController.get()
        assertFalse("Note type should not have changed yet", testEditor.noteTypeHasChanged())

        // Change the note type and make sure it registers as changed, but the database is unchanged
        var templateFront = testEditor.editText
        val testNoteTypeQfmtEdit = "!@#$%^&*TEST*&^%$#@!"
        templateFront.text.append(testNoteTypeQfmtEdit)
        advanceRobolectricLooper()
        assertTrue("Note type did not change after edit?", testEditor.noteTypeHasChanged())
        assertEquals(
            "Change already in database?",
            collectionBasicNoteTypeOriginal.toString().trim(),
            getCurrentDatabaseNoteTypeCopy(noteTypeName).toString().trim(),
        )

        // Kill and restart the Activity, make sure note type edit is preserved
        val outBundle = Bundle()
        templateEditorController.saveInstanceState(outBundle)
        templateEditorController.pause().stop().destroy()
        templateEditorController =
            Robolectric
                .buildActivity(CardTemplateEditor::class.java)
                .create(outBundle)
                .start()
                .resume()
                .visible()
        saveControllerForCleanup(templateEditorController)
        testEditor = templateEditorController.get()
        var shadowTestEditor = shadowOf(testEditor)
        assertTrue("note type change not preserved across activity lifecycle?", testEditor.noteTypeHasChanged())
        assertEquals(
            "Change already in database?",
            collectionBasicNoteTypeOriginal.toString().trim(),
            getCurrentDatabaseNoteTypeCopy(noteTypeName).toString().trim(),
        )

        // Make sure we get a confirmation dialog if we hit the back button
        assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(android.R.id.home))
        advanceRobolectricLooper()
        assertEquals("Wrong dialog shown?", getAlertDialogText(true), "Discard changes?")
        clickAlertDialogButton(checkDismissed = false) { negativeButton }
        advanceRobolectricLooper()
        assertTrue("note type change not preserved despite canceling back button?", testEditor.noteTypeHasChanged())

        // Make sure we things are cleared out after a cancel
        assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(android.R.id.home))
        assertEquals("Wrong dialog shown?", getAlertDialogText(true), "Discard changes?")
        clickAlertDialogButton(checkDismissed = false) { positiveButton }
        advanceRobolectricLooper()
        assertFalse("note type change not cleared despite discarding changes?", testEditor.noteTypeHasChanged())

        // Get going for content edit assertions again...
        templateEditorController =
            Robolectric
                .buildActivity(CardTemplateEditor::class.java, intent)
                .create()
                .start()
                .resume()
                .visible()
        saveControllerForCleanup(templateEditorController)
        testEditor = templateEditorController.get()
        shadowTestEditor = shadowOf(testEditor)
        templateFront = testEditor.editText
        templateFront.text.append(testNoteTypeQfmtEdit)
        advanceRobolectricLooper()
        assertTrue("Note type did not change after edit?", testEditor.noteTypeHasChanged())

        // Make sure we pass the edit to the Previewer
        assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_preview))
        advanceRobolectricLooper()
        val startedIntent = shadowTestEditor.nextStartedActivity
        val shadowIntent = shadowOf(startedIntent)
        advanceRobolectricLooper()
        assertEquals("Previewer not started?", CardViewerActivity::class.java.name, shadowIntent.intentClass.name)
        assertEquals(
            "Change already in database?",
            collectionBasicNoteTypeOriginal.toString().trim(),
            getCurrentDatabaseNoteTypeCopy(noteTypeName).toString().trim(),
        )
        shadowTestEditor.receiveResult(startedIntent, Activity.RESULT_OK, Intent())

        // Save the template then fetch it from the collection to see if it was saved correctly
        var testEditorNoteTypeEdited = testEditor.tempNoteType?.notetype
        advanceRobolectricLooper()
        assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_confirm))
        advanceRobolectricLooper()
        val collectionBasicNoteTypeCopyEdited = getCurrentDatabaseNoteTypeCopy(noteTypeName)
        testEditorNoteTypeEdited = col.notetypes.get(testEditorNoteTypeEdited!!.id)
        assertNotEquals("Note type is unchanged?", collectionBasicNoteTypeOriginal, collectionBasicNoteTypeCopyEdited)
        assertEquals(
            "note type did not save?",
            testEditorNoteTypeEdited.toString().trim(),
            collectionBasicNoteTypeCopyEdited.toString().trim(),
        )
        assertTrue("Note type does not have our change?", collectionBasicNoteTypeCopyEdited.toString().contains(testNoteTypeQfmtEdit))
    }

    @Test
    fun testDeleteTemplate() {
        val noteTypeName = "Basic (and reversed card)"

        // Start the CardTemplateEditor with a specific note type, and make sure the note type starts unchanged
        val collectionBasicNoteTypeOriginal = getCurrentDatabaseNoteTypeCopy(noteTypeName)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra("noteTypeId", collectionBasicNoteTypeOriginal.id)
        val templateEditorController =
            Robolectric
                .buildActivity(CardTemplateEditor::class.java, intent)
                .create()
                .start()
                .resume()
                .visible()
        saveControllerForCleanup(templateEditorController)
        val testEditor = templateEditorController.get()
        assertFalse("Note type should not have changed yet", testEditor.noteTypeHasChanged())
        assertEquals("Note type should have 2 templates now", 2, testEditor.tempNoteType?.templateCount)

        // Try to delete the template - click delete, click confirm for card delete, click confirm again for full sync
        val shadowTestEditor = shadowOf(testEditor)
        assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_delete))
        advanceRobolectricLooper()
        assertEquals("Wrong dialog shown?", "Delete the “Card 1” card type, and its 0 cards?", getAlertDialogText(true))
        clickAlertDialogButton { positiveButton }
        advanceRobolectricLooper()
        assertTrue("Note type should have changed", testEditor.noteTypeHasChanged())
        assertEquals("Note type should have 1 template now", 1, testEditor.tempNoteType?.templateCount)

        // Try to delete the template again, but there's only one
        assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_delete))
        advanceRobolectricLooper()
        assertEquals(
            "Did not show dialog about deleting only card?",
            getResourceString(CommonString.card_template_editor_cant_delete),
            getAlertDialogText(true),
        )
        assertEquals(
            "Change already in database?",
            collectionBasicNoteTypeOriginal.toString().trim(),
            getCurrentDatabaseNoteTypeCopy(noteTypeName).toString().trim(),
        )

        // Save the change to the database and make sure there's only one template after
        var testEditorNoteTypeEdited = testEditor.tempNoteType?.notetype
        assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_confirm))
        advanceRobolectricLooper()
        val collectionBasicNoteTypeCopyEdited = getCurrentDatabaseNoteTypeCopy(noteTypeName)
        testEditorNoteTypeEdited = col.notetypes.get(testEditorNoteTypeEdited!!.id)
        assertNotEquals("Note type is unchanged?", collectionBasicNoteTypeOriginal, collectionBasicNoteTypeCopyEdited)
        assertEquals(
            "Note type did not save?",
            testEditorNoteTypeEdited.toString().trim(),
            collectionBasicNoteTypeCopyEdited.toString().trim(),
        )
    }

    @Test
    @Throws(Exception::class)
    fun testTemplateAdd() {
        // Make sure we test previewing a new card template - not working for real yet
        val noteTypeName = "Basic"
        val collectionBasicNoteTypeOriginal = getCurrentDatabaseNoteTypeCopy(noteTypeName)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra("noteTypeId", collectionBasicNoteTypeOriginal.id)
        val templateEditorController =
            Robolectric
                .buildActivity(CardTemplateEditor::class.java, intent)
                .create()
                .start()
                .resume()
                .visible()
        saveControllerForCleanup(templateEditorController)
        val testEditor = templateEditorController.get()
        assertFalse("Ordinal pending add?", testEditor.tempNoteType.isOrdinalPendingAdd(0))

        // Try to add a template - click add, click confirm for card add, click confirm again for full sync
        val shadowTestEditor = shadowOf(testEditor)
        addCardType(testEditor, shadowTestEditor)
        // if AnkiDroid moves to match AnkiDesktop it will pop a dialog to confirm card create
        // Assert.assertEquals("Wrong dialog shown?", "This will create NN cards. Proceed?", getDialogText());
        // clickDialogButton(WhichButton.POSITIVE);
        assertTrue("Note type should have changed", testEditor.noteTypeHasChanged())
        assertNull("New templates have no database ordinal", testEditor.tempNoteType!!.getOriginalOrdinal(1))
        assertFalse("Ordinal pending add?", testEditor.tempNoteType.isOrdinalPendingAdd(0))
        assertTrue("Ordinal not pending add?", testEditor.tempNoteType.isOrdinalPendingAdd(1))
        assertEquals("Note type should have 2 templates now", 2, testEditor.tempNoteType!!.templateCount)

        // Make sure we pass the new template to the Previewer
        assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_preview))
        val startedIntent = shadowTestEditor.nextStartedActivity
        val shadowIntent = shadowOf(startedIntent)
        assertEquals("Previewer not started?", CardViewerActivity::class.java.name, shadowIntent.intentClass.name)
        assertEquals(
            "Change already in database?",
            collectionBasicNoteTypeOriginal.toString().trim(),
            getCurrentDatabaseNoteTypeCopy(noteTypeName).toString().trim(),
        )

        // Save the change to the database and make sure there are two templates after
        var testEditorNoteTypeEdited = col.notetypes.get(testEditor.tempNoteType!!.notetype.id)
        assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_confirm))
        advanceRobolectricLooper()
        val collectionBasicNoteTypeCopyEdited = getCurrentDatabaseNoteTypeCopy(noteTypeName)
        assertNotEquals("Note type is unchanged?", collectionBasicNoteTypeOriginal, collectionBasicNoteTypeCopyEdited)
        testEditorNoteTypeEdited = col.notetypes.get(testEditorNoteTypeEdited!!.id)

        assertEquals(
            "Note type did not save?",
            testEditorNoteTypeEdited.toString().trim(),
            collectionBasicNoteTypeCopyEdited.toString().trim(),
        )
    }

    /** Backend deletion policy is covered in CardTemplateEditingTest; this checks the warning UI. */
    @Test
    fun testDeleteTemplateWithSelectivelyGeneratedCards() {
        val noteType = col.notetypes.byName("Basic (optional reversed card)")!!
        val note =
            col.newNote(noteType).apply {
                setField(0, "Front")
                setField(1, "Back")
            }
        col.addNote(note)
        withCardTemplateEditor(noteType) {
            assertTrue("Unable to request deletion", shadowOf(this).clickMenuItem(R.id.action_delete))
            advanceRobolectricLooper()
            assertEquals("The orphan warning is shown", getResourceString(CommonString.orphan_note_message), getAlertDialogText(true))
            clickAlertDialogButton { positiveButton }
            assertFalse("A blocked deletion leaves the editor unchanged", noteTypeHasChanged())
            assertEquals("A blocked deletion preserves both templates", 2, tempNoteType!!.templateCount)
        }
    }

    @Test
    fun testDeletePendingAddExistingCardCount() {
        addBasicAndReversedNote()
        withCardTemplateEditor(col.notetypes.basicAndReversed) {
            selectTab(1)
            advanceRobolectricLooper()
            deleteSelectedCardType()
            addCardType(this, shadowOf(this))
            assertNull("The replacement has no database identity", tempNoteType!!.getOriginalOrdinal(1))

            assertTrue("Unable to request deletion", shadowOf(this).clickMenuItem(R.id.action_delete))
            advanceRobolectricLooper()
            assertEquals(
                "An unsaved replacement has no existing cards to delete",
                getQuantityString(CommonPlurals.card_template_editor_confirm_delete, 0, 0, CollectionManager.TR.cardTemplatesCard(2)),
                getAlertDialogText(true),
            )
        }
    }

    @Test
    fun testDeckOverride() {
        val noteTypeName = "Basic (optional reversed card)"
        val noteType = getCurrentDatabaseNoteTypeCopy(noteTypeName)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra("noteTypeId", noteType.id)
        val editor = super.startActivityNormallyOpenCollectionWithIntent(CardTemplateEditor::class.java, intent)
        val template = editor.tempNoteType?.getTemplate(0)
        MatcherAssert.assertThat("Deck ID element should exist", template?.jsonObject?.has("did"), Matchers.equalTo(true))
        MatcherAssert.assertThat("Deck ID element should be null", template?.jsonObject?.get("did"), Matchers.equalTo(JSONObject.NULL))
        editor.onDeckSelected(SelectableDeck.Deck(1, "hello"))
        MatcherAssert.assertThat("Deck ID element should be changed", template?.jsonObject?.get("did"), Matchers.equalTo(1L))
        editor.onDeckSelected(null)
        MatcherAssert.assertThat("Deck ID element should exist", template!!.jsonObject.has("did"), Matchers.equalTo(true))
        MatcherAssert.assertThat("Deck ID element should be null", template.jsonObject["did"], Matchers.equalTo(JSONObject.NULL))
    }

    @Test
    fun `editing with the keyboard open leaves Back to the IME - Issue 21807`() {
        withCardTemplateEditor {
            dispatchInsets(imeBottom = 240.dp)
            editText.text.append("edited")

            assertTrue("The template has unsaved changes", noteTypeHasChanged())
            assertFalse("Back should dismiss the keyboard", onBackPressedDispatcher.hasEnabledCallbacks())

            dispatchInsets()
            onBackPressedDispatcher.onBackPressed()
            assertEquals("Back should now confirm discarding edits", "Discard changes?", getAlertDialogText(true))
            assertFalse("Edits must not be discarded without confirmation", isFinishing)
        }
    }

    @Test
    fun `opening the keyboard after editing leaves Back to the IME - Issue 21807`() {
        withCardTemplateEditor {
            editText.text.append("edited")
            assertTrue("Back should confirm discarding edits", onBackPressedDispatcher.hasEnabledCallbacks())

            dispatchInsets(imeBottom = 240.dp)

            assertFalse("Back should dismiss the keyboard", onBackPressedDispatcher.hasEnabledCallbacks())
        }
    }

    /**
     * Guards the fix for 21807: a physical keyboard reports a visible IME, but does not hide the
     * tabs, and 'back' does not dismiss it.
     *
     * @see CardTemplateEditorScreenshotTest.PhysicalKeyboardIme
     */
    @Test
    fun `back confirms discarding edits with a physical keyboard`() {
        fun CardTemplateEditor.assertBackConfirmsDiscardingEdits() {
            editText.text.append("edited")

            assertTrue("Back should confirm discarding edits", onBackPressedDispatcher.hasEnabledCallbacks())
            onBackPressedDispatcher.onBackPressed()

            assertEquals("Back should confirm discarding edits", "Discard changes?", getAlertDialogText(true))
            assertFalse("Edits must not be discarded without confirmation", isFinishing)
            // dismiss: the next case would otherwise assert against this dialog if it showed none
            clickAlertDialogButton(checkDismissed = false) { negativeButton }
        }

        // 3-button navigation reports a visible IME with no height
        withCardTemplateEditor {
            dispatchInsets(navBarRight = 48.dp, imeVisible = true)
            assertBackConfirmsDiscardingEdits()
        }

        // gesture navigation shows only a 48dp strip
        withCardTemplateEditor {
            dispatchInsets(navBarBottom = 24.dp, imeBottom = 48.dp)
            assertBackConfirmsDiscardingEdits()
        }
    }

    /**
     * 'back' is driven by the selected page's tabs, so it must still follow the keyboard once
     * the pager moves to another card.
     */
    @Test
    fun `Back follows the keyboard after selecting another card - Issue 21807`() {
        withCardTemplateEditor(col.notetypes.basicAndReversed) {
            editText.text.append("edited")
            selectTab(1)
            advanceRobolectricLooper()

            dispatchInsets(imeBottom = 240.dp)
            assertFalse("Back should dismiss the keyboard", onBackPressedDispatcher.hasEnabledCallbacks())

            dispatchInsets()
            assertTrue("Back should confirm discarding edits", onBackPressedDispatcher.hasEnabledCallbacks())
        }
    }

    /** Deleting a card type recreates the pages with new adapter IDs. */
    @Test
    fun `Back follows the keyboard after deleting a card type - Issue 21807`() {
        withCardTemplateEditor(col.notetypes.basicAndReversed) {
            assertTrue("Unable to delete the card type", shadowOf(this).clickMenuItem(R.id.action_delete))
            advanceRobolectricLooper()
            clickAlertDialogButton { positiveButton }
            advanceRobolectricLooper()
            assertEquals("One card type should remain", 1, tempNoteType!!.templateCount)
            assertTrue("The deletion is unsaved", noteTypeHasChanged())

            dispatchInsets(imeBottom = 240.dp)
            assertEquals("The keyboard should hide the tabs", View.GONE, findViewById<View>(R.id.bottom_navigation).visibility)
            assertFalse("Back should dismiss the keyboard", onBackPressedDispatcher.hasEnabledCallbacks())

            dispatchInsets()
            onBackPressedDispatcher.onBackPressed()
            assertEquals("Back should now confirm discarding the deletion", "Discard changes?", getAlertDialogText(true))
            assertFalse("The deletion must not be discarded without confirmation", isFinishing)
        }
    }

    @Test
    fun `Ctrl+2 switches to the back template after deleting a card type`() {
        withCardTemplateEditor(col.notetypes.basicAndReversed) {
            assertTrue("Unable to delete the card type", shadowOf(this).clickMenuItem(R.id.action_delete))
            advanceRobolectricLooper()
            clickAlertDialogButton { positiveButton }
            advanceRobolectricLooper()
            assertEquals("One card type should remain", 1, tempNoteType!!.templateCount)

            val template = tempNoteType!!.getTemplate(0)
            assertEquals("The front template should be selected initially", template.qfmt, editText.text.toString())

            onKeyUp(KeyEvent.KEYCODE_2, KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_2, 0, KeyEvent.META_CTRL_ON))

            assertEquals("Ctrl+2 should show the back template", template.afmt, editText.text.toString())
        }
    }

    /** The tabs live in the fragment, which is present in both layouts */
    @Test
    fun `editing with the keyboard open leaves Back to the IME - tablet ui - Issue 21807`() =
        withSplitPaneUi {
            withCardTemplateEditor(col.notetypes.basicAndReversed) {
                editText.text.append("edited")
                assertTrue("Back should confirm discarding edits", onBackPressedDispatcher.hasEnabledCallbacks())

                dispatchInsets(imeBottom = 240.dp)

                assertFalse("Back should dismiss the keyboard", onBackPressedDispatcher.hasEnabledCallbacks())
            }
        }

    @Test
    fun `toolbar navigation still confirms discarding edits with the keyboard open`() {
        withCardTemplateEditor {
            dispatchInsets(imeBottom = 240.dp)
            editText.text.append("edited")

            assertTrue("Unable to click?", shadowOf(this).clickMenuItem(android.R.id.home))

            assertEquals("The toolbar should confirm discarding edits", "Discard changes?", getAlertDialogText(true))
            assertFalse("Edits must not be discarded without confirmation", isFinishing)
        }
    }

    @Test
    fun `closing the keyboard after reverting edits allows Back to exit`() {
        withCardTemplateEditor {
            val original = editText.text.toString()
            dispatchInsets(imeBottom = 240.dp)
            editText.text.append("edited")
            editText.setText(original)

            dispatchInsets()
            assertFalse("No discard confirmation is needed", onBackPressedDispatcher.hasEnabledCallbacks())
            onBackPressedDispatcher.onBackPressed()

            assertTrue("Back should close the unchanged editor", isFinishing)
        }
    }

    @Test
    fun `ensure 'Discard changes' dialog is enabled after note type changes - Issue 18518`() {
        fun assertDiscardChangesDialogShown(shadowEditor: ShadowActivity) {
            advanceRobolectricLooper()
            assertTrue("Unable to click?", shadowEditor.clickMenuItem(android.R.id.home))
            advanceRobolectricLooper()
            assertEquals("Wrong dialog shown?", "Discard changes?", getAlertDialogText(true))
            clickAlertDialogButton(checkDismissed = false) { positiveButton }
        }

        // Case 1: Show dialog after deck override
        withCardTemplateEditor {
            val shadowEditor = shadowOf(this)
            onDeckSelected(SelectableDeck.Deck(1, "hello"))
            assertDiscardChangesDialogShown(shadowEditor)
        }

        // Case 2: Show dialog after changing card browser appearance
        withCardTemplateEditor {
            val shadowEditor = shadowOf(this)
            assertTrue(
                "Unable to click?",
                shadowEditor.clickMenuItem(R.id.action_card_browser_appearance),
            )
            advanceRobolectricLooper()
            shadowEditor.receiveResult(
                shadowEditor.nextStartedActivity,
                Activity.RESULT_OK,
                Intent()
                    .putExtra(CardTemplateBrowserAppearanceEditor.EXTRA_QUESTION_FORMAT, "q")
                    .putExtra(CardTemplateBrowserAppearanceEditor.EXTRA_ANSWER_FORMAT, "a"),
            )
            assertDiscardChangesDialogShown(shadowEditor)
        }

        // Case 3: Show dialog after adding a card type
        withCardTemplateEditor {
            val shadowEditor = shadowOf(this)
            addCardType(this, shadowEditor)
            assertDiscardChangesDialogShown(shadowEditor)
        }
    }

    @Test
    fun testContentPreservedAfterChangingEditorView() {
        val noteTypeName = "Basic"

        // Start the CardTemplateEditor with a specific note type, and make sure the note type starts unchanged
        val collectionBasicNoteTypeOriginal = getCurrentDatabaseNoteTypeCopy(noteTypeName)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra("noteTypeId", collectionBasicNoteTypeOriginal.id)
        val templateEditorController =
            Robolectric
                .buildActivity(CardTemplateEditor::class.java, intent)
                .create()
                .start()
                .resume()
                .visible()
        saveControllerForCleanup(templateEditorController)
        val testEditor = templateEditorController.get()

        // Change the note type and make sure it registers as changed, but the database is unchanged
        val templateEditText = testEditor.editText
        val testNoteTypeQfmtEdit = "!@#$%^&*TEST*&^%$#@!"
        val updatedFrontContent = templateEditText.text.append(testNoteTypeQfmtEdit).toString()
        advanceRobolectricLooper()
        val cardTemplateFragment = testEditor.currentFragment
        val tempNoteType = testEditor.tempNoteType
        val cardId = 0
        // set Bottom Navigation View to Style
        cardTemplateFragment!!.setCurrentEditorView(R.id.styling_edit, cardId, tempNoteType!!.css)

        // set Bottom Navigation View to Front
        cardTemplateFragment.setCurrentEditorView(R.id.front_edit, cardId, tempNoteType.getTemplate(0).qfmt)

        // check if current content is updated or not
        assumeThat(templateEditText.text.toString(), Matchers.equalTo(updatedFrontContent))
    }

    @Test
    fun testSaveButtonEnabledAfterException() {
        withCardTemplateEditor(noteType = col.notetypes.cloze) {
            editText.setText("New Random Template Text")

            // throw an exception to simulate failure
            this.tempNoteType = null

            confirmButton.performClick()

            shadowOf(Looper.getMainLooper()).idle()

            assertTrue("Button should be clickable after failure", confirmButton.isClickable)
            assertTrue("Button should be enabled after failure", confirmButton.isEnabled)
        }
    }

    @Test
    fun testBottomNavigationViewLayoutTransition() {
        val noteTypeName = "Basic"

        // Start the CardTemplateEditor with a specific note type, and make sure the note type starts unchanged
        val collectionBasicNoteTypeOriginal = getCurrentDatabaseNoteTypeCopy(noteTypeName)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra("noteTypeId", collectionBasicNoteTypeOriginal.id)
        val templateEditorController =
            Robolectric
                .buildActivity(CardTemplateEditor::class.java, intent)
                .create()
                .start()
                .resume()
                .visible()
        saveControllerForCleanup(templateEditorController)
        val testEditor = templateEditorController.get()

        // Change the note type and make sure it registers as changed, but the database is unchanged
        val templateEditText = testEditor.editText
        advanceRobolectricLooper()
        val cardTemplateFragment = testEditor.currentFragment
        val tempNoteType = testEditor.tempNoteType

        // check if current view is front(default) view
        assumeThat(templateEditText.text.toString(), Matchers.equalTo(tempNoteType!!.getTemplate(0).qfmt))
        assumeThat(cardTemplateFragment!!.currentEditorViewId, Matchers.equalTo(R.id.front_edit))

        val cardId = 0
        // set Bottom Navigation View to Style
        cardTemplateFragment.setCurrentEditorView(R.id.styling_edit, cardId, tempNoteType.css)

        // check if current view is changed or not
        assumeThat(templateEditText.text.toString(), Matchers.equalTo(tempNoteType.css))
        assumeThat(cardTemplateFragment.currentEditorViewId, Matchers.equalTo(R.id.styling_edit))
    }

    @Test
    fun testInsertFieldInCorrectFragmentAfterNavigation() {
        val noteTypeName = "Basic (and reversed card)"

        // Start the CardTemplateEditor with a note type that has multiple templates
        val collectionBasicNoteTypeOriginal = getCurrentDatabaseNoteTypeCopy(noteTypeName)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra("noteTypeId", collectionBasicNoteTypeOriginal.id)
        val templateEditorController =
            Robolectric
                .buildActivity(CardTemplateEditor::class.java, intent)
                .create()
                .start()
                .resume()
                .visible()
        saveControllerForCleanup(templateEditorController)
        val testEditor = templateEditorController.get()

        assertEquals("Note type should have 2 templates", 2, testEditor.tempNoteType?.templateCount)

        // Start on first fragment (Card 1)
        assertEquals("Should start on first fragment", 0, testEditor.viewPager.currentItem)
        advanceRobolectricLooper()

        val firstFragment = testEditor.currentFragment
        assertNotNull("First fragment should exist", firstFragment)
        // Use fragment's view to get EditText (activity.findViewById may return wrong view with offscreenPageLimit)
        val firstTemplateEditText = firstFragment!!.requireView().findViewById<EditText>(R.id.edit_text)
        val originalFirstContent = firstTemplateEditText.text.toString()

        // Navigate to second fragment (Card 2)
        testEditor.viewPager.currentItem = 1
        advanceRobolectricLooper()

        val secondFragment = testEditor.currentFragment
        assertNotNull("Second fragment should exist", secondFragment)
        // Use fragment's view to get EditText (activity.findViewById may return wrong view with offscreenPageLimit)
        val secondTemplateEditText = secondFragment!!.requireView().findViewById<EditText>(R.id.edit_text)
        val originalSecondContent = secondTemplateEditText.text.toString()

        // Navigate back to first fragment
        testEditor.viewPager.currentItem = 0
        advanceRobolectricLooper()

        val firstFragmentAgain = testEditor.currentFragment
        assertNotNull("First fragment should exist after navigation back", firstFragmentAgain)
        assertEquals("Should be back on first fragment", 0, testEditor.viewPager.currentItem)

        // Insert a field into the first fragment
        val fieldToInsert = "Front"
        val expectedFieldText = "{{$fieldToInsert}}"

        // Simulate showing the insert field dialog and selecting a field
        firstFragmentAgain!!.showInsertFieldDialog()
        advanceRobolectricLooper()

        val resultBundle = Bundle()
        resultBundle.putString(InsertFieldDialog.RESULT_INSERTED_FIELD, expectedFieldText)
        testEditor.supportFragmentManager.setFragmentResult(firstFragmentAgain.insertFieldRequestKey, resultBundle)
        advanceRobolectricLooper()

        // Verify the field was inserted into the first fragment
        val updatedFirstContent = firstTemplateEditText.text.toString()
        assertTrue(
            "Field should be inserted into first fragment",
            updatedFirstContent.contains(expectedFieldText),
        )
        assertTrue(
            "First fragment content should have changed",
            updatedFirstContent != originalFirstContent,
        )

        // Navigate to second fragment to verify it wasn't modified
        testEditor.viewPager.currentItem = 1
        advanceRobolectricLooper()

        // Use currentFragment's view to get EditText
        val secondFragmentAfter = testEditor.currentFragment
        assertNotNull("Second fragment should exist after navigation", secondFragmentAfter)
        val secondTemplateEditTextAfter = secondFragmentAfter!!.requireView().findViewById<EditText>(R.id.edit_text)
        val secondContentAfter = secondTemplateEditTextAfter.text.toString()

        assertEquals(
            "Second fragment should not have been modified",
            originalSecondContent,
            secondContentAfter,
        )
        assertFalse(
            "Field should NOT be in second fragment",
            secondContentAfter.contains(expectedFieldText),
        )
    }

    @Test
    fun `tab changes succeed with tablet UI - Issue 19589`() =
        withSplitPaneUi {
            withCardTemplateEditor(col.notetypes.basicAndReversed) {
                selectTab(1)
                selectTab(0)

                assertThat(selectedTabPosition, equalTo(0))
            }
        }

    @Test
    fun `correct ord is previewed after tab change - tablet ui - issue 20097`() =
        withSplitPaneUi {
            withCardTemplateEditor(col.notetypes.basicAndReversed) {
                selectTab(1)

                assertThat(previewer.ord, equalTo(1))
            }
        }

    @Test
    fun `the current card is focused`() {
        withCardTemplateEditor(noteType = getCurrentDatabaseNoteTypeCopy("Basic (and reversed card)")) {
            advanceRobolectricLooper()
            assertThat("card 1 is focused on open", window.decorView.findFocus(), equalTo(templateFragment(0).binding.editText))

            selectTab(1)
            advanceRobolectricLooper()
            assertThat("card 2 is focused once selected", window.decorView.findFocus(), equalTo(templateFragment(1).binding.editText))

            selectTab(0)
            advanceRobolectricLooper()
            assertThat("card 1 is focused again", window.decorView.findFocus(), equalTo(templateFragment(0).binding.editText))
        }
    }

    @Test
    fun `adding a second card type still counts the notes`() {
        addBasicNote()
        val intent = Intent(Intent.ACTION_VIEW).putExtra("noteTypeId", col.notetypes.byName("Basic")!!.id)
        val controller =
            Robolectric
                .buildActivity(CardTemplateEditor::class.java, intent)
                .create()
                .start()
                .resume()
                .visible()
        saveControllerForCleanup(controller)
        val editor = controller.get()

        addCardType(editor, shadowOf(editor))
        assertTrue("Unable to click?", shadowOf(editor).clickMenuItem(R.id.action_add))
        advanceRobolectricLooper()
        assertEquals(
            "Adding from a new card type should count the notes",
            getQuantityString(CommonPlurals.card_template_editor_confirm_add, 1, 1),
            getAlertDialogText(true),
        )
    }

    @Test
    fun `adding an optional reverse template with an empty condition creates no cards`() {
        val noteType = col.notetypes.byName("Basic (optional reversed card)")!!
        val note = col.newNote(noteType)
        note.setField(0, "Front")
        note.setField(1, "Back")
        note.setField(2, "")
        col.addNote(note)
        assertEquals("Only the forward card should exist", 1, getNoteTypeCardCount(noteType))

        withCardTemplateEditor(noteType) {
            selectTab(1)
            advanceRobolectricLooper()
            assertEquals("The optional reverse template should be selected", 1, selectedTabPosition)

            assertTrue("Unable to add a card type", shadowOf(this).clickMenuItem(R.id.action_add))
            advanceRobolectricLooper()
            val confirmation = getAlertDialogText(true)
            clickAlertDialogButton { positiveButton }
            assertEquals("The duplicate template should be added", 3, tempNoteType!!.templateCount)

            // Saving rejects identical fronts. Keep the empty condition while making the new front distinct.
            advanceRobolectricLooper()
            templateFragment(2).binding.editText.setText("{{#Add Reverse}}{{Front}} {{Back}}{{/Add Reverse}}")

            assertTrue("Unable to save the templates", shadowOf(this).clickMenuItem(R.id.action_confirm))
            advanceRobolectricLooper()
            val savedNoteType = col.notetypes.get(noteType.id)!!
            assertEquals("The duplicate template should be saved", 3, savedNoteType.templates.length())
            assertEquals("An empty Add Reverse field should prevent generating another card", 1, getNoteTypeCardCount(noteType))
            assertEquals(
                "The confirmation must not promise a card that will not be generated",
                getQuantityString(CommonPlurals.card_template_editor_confirm_add, 0, 0),
                confirmation,
            )
        }
    }

    @Test
    fun `adding from a broken template does not crash`() {
        addBasicAndReversedNote()
        withCardTemplateEditor(col.notetypes.basicAndReversed) {
            templateFragment(0).binding.editText.setText("{{#Front}}")
            assertTrue("Unable to add a card type", shadowOf(this).clickMenuItem(R.id.action_add))
            advanceRobolectricLooper()
            assertEquals(
                "A broken template should not create cards",
                getQuantityString(CommonPlurals.card_template_editor_confirm_add, 0, 0),
                getAlertDialogText(true),
            )
        }
    }

    @Test
    fun `deleting the first card type keeps the cards of the second after recreation`() {
        val note = addBasicAndReversedNote()
        val reverseCardId = note.cards().single { it.ord == 1 }.id
        val intent = CardEditor(ntid = col.notetypes.basicAndReversed.id).toIntent(targetContext)
        val controller = startActivityControllerNormallyOpenCollectionWithIntent(CardTemplateEditor::class.java, intent)

        controller.get().deleteSelectedCardType()
        controller.recreate()
        advanceRobolectricLooper()
        controller.get().saveChanges()

        assertEquals("Only the deleted card type's card should be removed", listOf(reverseCardId), note.cids())
    }

    private fun CardTemplateEditor.deleteSelectedCardType() {
        assertTrue("Unable to delete the card type", shadowOf(this).clickMenuItem(R.id.action_delete))
        advanceRobolectricLooper()
        clickAlertDialogButton { positiveButton }
        advanceRobolectricLooper()
    }

    private fun CardTemplateEditor.saveChanges() {
        assertTrue("Unable to save", shadowOf(this).clickMenuItem(R.id.action_confirm))
        advanceRobolectricLooper()
    }

    private fun CardTemplateEditor.templateFragment(ord: Int) = supportFragmentManager.findFragmentByTag("f$ord") as CardTemplateFragment

    private fun addCardType(
        testEditor: CardTemplateEditor,
        shadowTestEditor: ShadowActivity,
    ) {
        assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_add))
        advanceRobolectricLooper()
        val ordinal = testEditor.mainBinding.cardTemplateEditorPager.currentItem
        val numAffectedCards =
            if (!testEditor.tempNoteType.isOrdinalPendingAdd(ordinal)) {
                col.notetypes.tmplUseCount(testEditor.tempNoteType!!.notetype, ordinal)
            } else {
                0
            }
        assertEquals(
            "Did not show dialog about adding template and it's card?",
            getQuantityString(CommonPlurals.card_template_editor_confirm_add, numAffectedCards, numAffectedCards),
            getAlertDialogText(true),
        )
        clickAlertDialogButton { positiveButton }
    }

    @Test
    fun `template to markdown`() {
        val template =
            CardTemplate(
                front = "Hello World{{Front}}\n{{Extra}}",
                back = "{{FrontSide}}\n",
                style = ".card { }",
            )

        assertEquals(
            "markdown formatted template",
"""
**Front template**

```html
Hello World{{Front}}
{{Extra}}
```

**Back template**

```html
{{FrontSide}}

```

**Styling**

```css
.card { }
```
""".trim(),
            template.toMarkdown(targetContext),
        )
    }

    private fun getNoteTypeCardCount(notetype: NotetypeJson): Int {
        var cardCount = 0
        for (noteId in col.notetypes.nids(notetype)) {
            cardCount += col.getNote(noteId).numberOfCards()
        }
        return cardCount
    }

    private fun CardTemplateNotetype?.isOrdinalPendingAdd(ordinal: Int): Boolean {
        requireNotNull(this)
        return CardTemplateNotetype.isOrdinalPendingAdd(this, ordinal)
    }

    private suspend fun NotetypeJson.getCardIds(vararg ords: Int): List<Long>? = withCol { notetypes.getCardIdsForNoteType(id, ords) }
}

private val CardTemplateEditor.editText: EditText
    get() = this.findViewById(R.id.edit_text)

private val CardTemplateEditor.viewPager
    get() = this.mainBinding.cardTemplateEditorPager

fun RobolectricTest.withCardTemplateEditor(
    noteType: NotetypeJson = col.notetypes.basic,
    block: CardTemplateEditor.() -> Unit,
) {
    val intent = CardEditor(ntid = noteType.id).toIntent(targetContext)
    val activity = startActivityNormallyOpenCollectionWithIntent(CardTemplateEditor::class.java, intent)
    block(activity)
}

fun CardTemplateEditor.selectTab(index: Int) = topBinding.slidingTabs.selectTab(index)

val CardTemplateEditor.selectedTabPosition: Int get() = topBinding.slidingTabs.selectedTabPosition

val CardTemplateEditor.previewer: TemplatePreviewerFragment
    get() = this.supportFragmentManager.findFragmentById(R.id.fragment_container) as TemplatePreviewerFragment

val TemplatePreviewerFragment.ord: CardOrdinal
    get() = this.viewModel.ordFlow.value

val CardTemplateEditor.confirmButton: View
    get() = findViewById(R.id.action_confirm)
