// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>

package com.ichi2.anki.dialogs

import android.content.ContextWrapper
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CommonString
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.dialogs.CreateDeckDialog.DeckDialogType
import com.ichi2.anki.dialogs.utils.input
import com.ichi2.anki.libanki.DeckId
import com.ichi2.testutils.EmptyAnkiActivity
import com.ichi2.testutils.ext.snackbarText
import com.ichi2.utils.getInputTextLayout
import com.ichi2.utils.positiveButton
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.Matcher
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowToast
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.fail
import com.ichi2.anki.common.android.R as CommonR

@RunWith(RobolectricTestRunner::class)
class CreateDeckDialogTest : RobolectricTest() {
    private lateinit var activity: EmptyAnkiActivity

    override fun setUp() {
        super.setUp()
        ensureCollectionLoadIsSynchronous()
        activity = startRegularActivity()
        activity.setTheme(CommonR.style.Theme_Light)
        activity.setContentView(
            CoordinatorLayout(activity).apply {
                id = CommonR.id.root_layout
            },
        )
    }

    private fun withActivity(block: (EmptyAnkiActivity) -> Unit) {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        block(activity)
    }

    @Test
    fun `creating a child of a filtered deck shows an error`() {
        addDynamicDeck("Filtered Deck 1")
        val deckCount = col.decks.count()
        withCreateDeckDialog(DeckDialogType.DECK) {
            onNewDeckCreated = { fail("the deck was not created") }
            showDialog().apply {
                input = "Filtered Deck 1::Child"
                positiveButton.performClick()
            }
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertThat("no deck was created", col.decks.count(), equalTo(deckCount))
        withActivity { activity ->
            assertThat(
                activity.snackbarText,
                equalTo(TR.errorsFilteredParentDeck()),
            )
        }
    }

    @Test
    fun `creating a descendant of a filtered deck with non-activity context shows an error`() {
        addDynamicDeck("Filtered Deck 1")
        val deckCount = col.decks.count()
        withActivity { activity ->
            val dialog = CreateDeckDialog(ContextWrapper(activity), "Create deck", DeckDialogType.DECK, null)
            dialog.onNewDeckCreated = { fail("the deck was not created") }
            dialog.createDeck("filtered deck 1::Parent::Child")

            assertThat("no deck was created", col.decks.count(), equalTo(deckCount))
            assertThat(
                ShadowToast.getTextOfLatestToast(),
                equalTo(TR.errorsFilteredParentDeck()),
            )
        }
    }

    @Test
    fun `creating a subdeck below a filtered deck shows an error`() {
        val parentId = addDeck("Parent")
        addDynamicDeck("Parent::Filtered")
        val deckCount = col.decks.count()
        withCreateDeckDialog(DeckDialogType.SUB_DECK, parentId) {
            onNewDeckCreated = { fail("the deck was not created") }
            showDialog().apply {
                input = "Filtered::Child"
                positiveButton.performClick()
            }
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertThat("no deck was created", col.decks.count(), equalTo(deckCount))
        withActivity { activity ->
            assertThat(
                activity.snackbarText,
                equalTo(TR.errorsFilteredParentDeck()),
            )
        }
    }

    @Test
    fun `deck ordering hint`() {
        // The correct way to order a deck is ['01', '02', '10']
        val expectedText = "If you have deck ordering issues (e.g. ‘10’ appears before ‘2’), replace ‘2’ with ‘02’"
        testDialog(DeckDialogType.DECK) {
            fun assertHelperText(
                reason: String?,
                matcher: Matcher<in CharSequence?>,
            ) = assertThat(reason, getInputTextLayout().helperText, matcher)

            input = "test"
            assertHelperText("no number suggestion if text-only", nullValue())
            input = "1. Cheese"
            assertHelperText("no number suggestion if number is less than 10", nullValue())
            input = "10. Cheese"
            assertHelperText(
                "Number suggestion if number is greater than or equal to 10",
                equalTo(expectedText),
            )
            input = "1. Cheese"
            assertHelperText("hint is removed if the number is removed", nullValue())
        }
    }

    @Test
    fun nameMayNotBeZeroLength() {
        testDialog(DeckDialogType.DECK) {
            assertThat("Ok is disabled if zero length input", positiveButton.isEnabled, equalTo(false))
            input = "NotEmpty"
            assertThat("Ok is enabled if not zero length input", positiveButton.isEnabled, equalTo(true))
            input = "A::B"
            assertThat("OK is enabled if fully qualified input provided ('A::B')", positiveButton.isEnabled, equalTo(true))
        }
    }

    @Test
    fun `subdeck name may not be blank`() {
        val parentId = addDeck("Parent")
        testDialog(DeckDialogType.SUB_DECK, parentId) {
            input = "   "
            assertThat("Create is disabled if the subdeck name is blank", positiveButton.isEnabled, equalTo(false))
            input = "Child"
            assertThat("Create is enabled if the subdeck name is not blank", positiveButton.isEnabled, equalTo(true))
        }
    }

    @Test
    fun `renaming a deck to an existing deck name shows an error`() {
        col.decks.id("c")
        testRenameDialog(currentName = "a::b") {
            input = "c"
            assertThat(
                "error is displayed",
                getInputTextLayout().error?.toString(),
                equalTo(getResourceString(CommonString.error_name_exists)),
            )
            assertThat("rename is disabled", positiveButton.isEnabled, equalTo(false))
        }
    }

    @Test
    fun `renaming a deck to an equivalent name changes nothing`() {
        val deckId = col.decks.id("a::b")
        val deckCount = col.decks.count()
        withRenameDeckDialog(deckId) {
            deckName = "a::b"
            onNewDeckCreated = { fail("the deck was not renamed") }
            renameDeck("a:::b")
        }
        assertThat("the deck kept its name", col.decks.name(deckId), equalTo("a::b"))
        assertThat("no deck was created", col.decks.count(), equalTo(deckCount))
        withActivity { activity ->
            assertThat("no rename is reported", activity.snackbarText, nullValue())
        }
    }

    @Test
    fun `renaming a deck which no longer exists does not create a deck`() {
        val deckId = col.decks.id("Old Deck")
        col.decks.remove(listOf(deckId))
        val deckCount = col.decks.count()
        withRenameDeckDialog(deckId) {
            deckName = "Old Deck"
            onNewDeckCreated = { fail("the deck was not renamed") }
            renameDeck("New Deck")
        }
        assertThat("no deck was created", col.decks.count(), equalTo(deckCount))
    }

    private fun testRenameDialog(
        currentName: String,
        callback: (AlertDialog.() -> Unit),
    ) {
        val deckId = col.decks.id(currentName)
        withRenameDeckDialog(deckId) {
            deckName = currentName
            callback(this.showDialog())
        }
    }

    /**
     * Executes [callback] on the [AlertDialog] created from [CreateDeckDialog]
     */
    private fun testDialog(
        deckDialogType: DeckDialogType,
        parentId: DeckId? = null,
        renamedDeckId: DeckId? = null,
        callback: (AlertDialog.() -> Unit),
    ) {
        withCreateDeckDialog(deckDialogType, parentId, renamedDeckId) {
            callback(this.showDialog())
        }
    }

    /**
     * Creates a test instance of [CreateDeckDialog]
     */
    private fun withCreateDeckDialog(
        deckDialogType: DeckDialogType,
        parentId: DeckId? = null,
        renamedDeckId: DeckId? = null,
        callback: (CreateDeckDialog.() -> Unit),
    ) {
        withActivity { activity: EmptyAnkiActivity ->
            val createDeckDialog =
                CreateDeckDialog(
                    context = activity,
                    title = "Create deck",
                    deckDialogType = deckDialogType,
                    parentId = parentId,
                    renamedDeckId = renamedDeckId,
                )
            callback(createDeckDialog)
        }
    }

    /**
     * Creates a test instance of [CreateDeckDialog] which renames [renamedDeckId]
     */
    private fun withRenameDeckDialog(
        renamedDeckId: DeckId,
        callback: (CreateDeckDialog.() -> Unit),
    ) = withCreateDeckDialog(DeckDialogType.RENAME_DECK, renamedDeckId = renamedDeckId, callback = callback)

    /*
     * The next 8 testcases test every permutation of
     * {createDeck, renameDeck}, {validName, invalidName}, [activityContext, nonActivityContext]
     * Shadows.shadowOf(Looper.getMainLooper()).idle()
     * is used to flush the queue and get the latest snackbar, since asking for it immediately fails the test
     * as the display time is LENGTH_LONG
     */
    @Test
    fun `createDeck with activity context shows snackbar for valid name`() {
        ensureExecutionOfScenario(DeckDialogType.DECK) { dialog, assertionCalled ->
            dialog.onNewDeckCreated = { _: DeckId -> assertionCalled() }
            dialog.createDeck("Create Deck")

            withActivity { activity ->
                assertThat(
                    "Snackbar should confirm deck creation for valid name",
                    activity.snackbarText,
                    equalTo(getResourceString(CommonString.deck_created)),
                )
            }
        }
    }

    @Test
    fun `createDeck with activity context shows snackbar for invalid name`() {
        withActivity { activity ->
            val dialog = CreateDeckDialog(activity, "Create deck", DeckDialogType.DECK, null)
            dialog.onNewDeckCreated = { _: DeckId -> }
            dialog.createDeck("   ")
            Shadows.shadowOf(Looper.getMainLooper()).idle()

            assertThat(
                "Snackbar should show invalid name error for blank name",
                activity.snackbarText,
                equalTo(getResourceString(CommonString.invalid_deck_name)),
            )
        }
    }

    @Test
    fun `createSubDeck with a blank name does not create a deck`() {
        val parentId = addDeck("Parent")
        val deckCount = col.decks.count()
        withActivity { activity ->
            val dialog = CreateDeckDialog(activity, "Create subdeck", DeckDialogType.SUB_DECK, parentId)
            dialog.onNewDeckCreated = { fail("the deck was not created") }
            dialog.createSubDeck(parentId, "   ")
            Shadows.shadowOf(Looper.getMainLooper()).idle()

            assertThat("no deck was created", col.decks.count(), equalTo(deckCount))
            assertThat(
                "Snackbar should show invalid name error for blank name",
                activity.snackbarText,
                equalTo(getResourceString(CommonString.invalid_deck_name)),
            )
        }
    }

    @Test
    fun `createDeck with non-activity context shows toast for valid name`() {
        withActivity { activity ->
            val dialog =
                CreateDeckDialog(ContextWrapper(activity), "Create deck", DeckDialogType.DECK, null)
            dialog.onNewDeckCreated = { _: DeckId -> }
            dialog.createDeck("Create Deck")

            assertThat(
                "Toast should confirm deck creation for valid name",
                ShadowToast.getTextOfLatestToast(),
                equalTo(getResourceString(CommonString.deck_created)),
            )
        }
    }

    @Test
    fun `createDeck with non-activity context shows toast for invalid name`() {
        withActivity { activity ->
            val dialog =
                CreateDeckDialog(ContextWrapper(activity), "Create deck", DeckDialogType.DECK, null)
            dialog.onNewDeckCreated = { _: DeckId -> }
            dialog.createDeck("   ")

            assertThat(
                "Toast should show invalid name error for blank name",
                ShadowToast.getTextOfLatestToast(),
                equalTo(getResourceString(CommonString.invalid_deck_name)),
            )
        }
    }

    @Test
    fun `renameDeck with activity context shows snackbar for valid name`() {
        val deckId = col.decks.id("Old Deck")
        ensureExecutionOfScenario(DeckDialogType.RENAME_DECK, renamedDeckId = deckId) { dialog, assertionCalled ->
            dialog.deckName = "Old Deck"
            dialog.onNewDeckCreated = { _: DeckId -> assertionCalled() }
            dialog.renameDeck("Rename Deck")

            withActivity { activity ->
                assertThat(
                    "Snackbar should confirm rename for valid name",
                    activity.snackbarText,
                    equalTo(getResourceString(CommonString.deck_renamed)),
                )
            }
        }
    }

    @Test
    fun `renameDeck with activity context shows snackbar for invalid name`() {
        withActivity { activity ->
            val dialog = CreateDeckDialog(activity, "Create deck", DeckDialogType.RENAME_DECK, null, col.decks.id("Old Deck"))
            dialog.deckName = "Old Deck"
            dialog.onNewDeckCreated = { _: DeckId -> }
            dialog.renameDeck("   ")
            Shadows.shadowOf(Looper.getMainLooper()).idle()

            assertThat(
                "Snackbar should show invalid name error for blank name",
                activity.snackbarText,
                equalTo(getResourceString(CommonString.invalid_deck_name)),
            )
        }
    }

    @Test
    fun `renameDeck with non-activity context shows toast for valid name`() {
        withActivity { activity ->
            val dialog =
                CreateDeckDialog(
                    ContextWrapper(activity),
                    "Create deck",
                    DeckDialogType.RENAME_DECK,
                    null,
                    col.decks.id("Old Deck"),
                )
            dialog.deckName = "Old Deck"
            dialog.onNewDeckCreated = { _: DeckId -> }
            dialog.renameDeck("Rename Deck")

            assertThat(
                "Toast should confirm rename for valid name",
                ShadowToast.getTextOfLatestToast(),
                equalTo(getResourceString(CommonString.deck_renamed)),
            )
        }
    }

    @Test
    fun `renameDeck with non-activity context shows toast for invalid name`() {
        withActivity { activity ->
            val dialog =
                CreateDeckDialog(
                    ContextWrapper(activity),
                    "Create deck",
                    DeckDialogType.RENAME_DECK,
                    null,
                    col.decks.id("Old Deck"),
                )
            dialog.deckName = "Old Deck"
            dialog.onNewDeckCreated = { _: DeckId -> }
            dialog.renameDeck("   ")

            assertThat(
                "Toast should show invalid name error for blank name",
                ShadowToast.getTextOfLatestToast(),
                equalTo(getResourceString(CommonString.invalid_deck_name)),
            )
        }
    }

    /**
     * Tests a scenario with a [EmptyAnkiActivity] hosting a [CreateDeckDialog].
     * The second parameter of the callback ('assertionCalled') must be called for this to pass
     */
    private fun ensureExecutionOfScenario(
        deckDialogType: DeckDialogType,
        parentId: DeckId? = null,
        renamedDeckId: DeckId? = null,
        callback: ((CreateDeckDialog, (() -> Unit)) -> Unit),
    ) {
        withActivity { activity: EmptyAnkiActivity ->
            val assertionCalled = AtomicReference(false)
            callback(CreateDeckDialog(activity, "Create deck", deckDialogType, parentId, renamedDeckId)) {
                assertionCalled.set(true)
            }
            assertThat("no call to assertionCalled()", assertionCalled.get(), equalTo(true))
        }
    }
}

/** Test of [CreateDeckDialog] */
class CreateDeckDialogNonAndroidTest {
    @Test
    fun `number larger than nine detection`() {
        fun assertLargerThanNine(
            reason: String?,
            input: String,
            result: Boolean,
        ) = assertThat(reason, input.containsNumberLargerThanNine(), equalTo(result))

        assertLargerThanNine("empty string", "", false)
        assertLargerThanNine("text", "deck name", false)
        assertLargerThanNine("less than ten", "9. - Chemicals", false)
        assertLargerThanNine("Ten or greater", "10. - Chemicals", true)
        assertLargerThanNine("Ten or greater", "99. - Chemicals", true)
        assertLargerThanNine("zero prefix", "09. - Chemicals", false)
        assertLargerThanNine("time", "10:50:59", false)
        assertLargerThanNine("time", "Filtered Deck 22:34", false)
        assertLargerThanNine("suffix", "Deck 34", true)
    }
}
