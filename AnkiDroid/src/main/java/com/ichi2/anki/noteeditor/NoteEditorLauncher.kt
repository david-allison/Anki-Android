/*
 *  Copyright (c) 2024 Sanjay Sargam <sargamsanjaykumar@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.noteeditor

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.NoteEditorActivity
import com.ichi2.anki.NoteEditorFragment
import com.ichi2.anki.NoteEditorFragment.Companion.NoteEditorCaller
import com.ichi2.anki.common.destinations.NoteEditorDestination
import com.ichi2.anki.common.ui.TransitionDirection
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.utils.Destination

/**
 * Defines various configurations for opening the NoteEditor fragment with specific data or actions.
 */
sealed interface NoteEditorLauncher : Destination {
    override fun toIntent(context: Context): Intent = toIntent(context, action = null)

    /**
     * Generates an intent to open the NoteEditor activity with the configured parameters
     *
     * @param context The context from which the intent is launched.
     * @param action Optional action string for the intent.
     * @return Intent configured to launch the NoteEditor  activity.
     */
    fun toIntent(
        context: Context,
        action: String? = null,
    ) = Intent(context, NoteEditorActivity::class.java).apply {
        putExtras(toBundle())
        action?.let { this.action = it }
    }

    /**
     * Converts the configuration into a Bundle to pass arguments to the NoteEditor fragment.
     *
     * @return Bundle containing arguments specific to this configuration.
     */
    fun toBundle(): Bundle

    /**
     * Represents adding a note to the NoteEditor from the card browser.
     * @property searchTerms The current search query in the card browser, pre-populated as note text.
     * @property lastDeckId The most recently selected deck in the card browser, if any.
     */
    data class AddNoteFromCardBrowser(
        val searchTerms: String,
        val lastDeckId: DeckId?,
    ) : NoteEditorLauncher {
        override fun toBundle(): Bundle =
            Bundle().apply {
                putInt(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.CARDBROWSER_ADD.value)
                putString(NoteEditorFragment.EXTRA_TEXT_FROM_SEARCH_VIEW, searchTerms)
                putBoolean(NoteEditorFragment.IN_CARD_BROWSER_ACTIVITY, false)
                if (lastDeckId != null && lastDeckId > 0) {
                    putLong(NoteEditorFragment.EXTRA_DID, lastDeckId)
                }
            }
    }

    /**
     * Opens the NoteEditor for the current selection (card or note).
     * @property cardIds The selected card ID when editing a card, or the IDs of cards of the same note when editing a note.
     * @property animation The animation direction.
     * @property inCardBrowserActivity True if opened within Card Browser Activity.
     */
    data class EditSelection(
        val cardIds: List<CardId>,
        val animation: TransitionDirection,
        val inCardBrowserActivity: Boolean = false,
    ) : NoteEditorLauncher {
        override fun toBundle(): Bundle =
            Bundle().apply {
                putInt(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.EDIT.value)
                // To handle single card selection
                putLong(NoteEditorFragment.EXTRA_CARD_ID, cardIds.first())
                // To handle multi select and note edit
                putLongArray(NoteEditorFragment.EXTRA_CARD_IDS, cardIds.toLongArray())
                putParcelable(AnkiActivity.FINISH_ANIMATION_EXTRA, animation as Parcelable)
                putBoolean(NoteEditorFragment.IN_CARD_BROWSER_ACTIVITY, inCardBrowserActivity)
            }
    }
}

fun NoteEditorDestination.toIntent(context: Context): Intent =
    when (this) {
        is NoteEditorDestination.AddNote ->
            Intent(context, NoteEditorActivity::class.java).apply {
                putExtra(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.DECKPICKER.value)
                deckId?.let { putExtra(NoteEditorFragment.EXTRA_DID, it) }
            }
        is NoteEditorDestination.ImageOcclusion ->
            Intent(context, NoteEditorActivity::class.java).also { intent ->
                intent.putExtra(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.IMG_OCCLUSION.value)
                intent.putExtra(NoteEditorFragment.EXTRA_IMG_OCCLUSION, imageUri)
            }
        is NoteEditorDestination.AddInstantNote ->
            Intent(context, NoteEditorActivity::class.java).also { intent ->
                intent.putExtra(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.INSTANT_NOTE_EDITOR.value)
                intent.putExtra(Intent.EXTRA_TEXT, sharedText)
            }
        is NoteEditorDestination.EditNoteFromPreviewer ->
            Intent(context, NoteEditorActivity::class.java).also { intent ->
                intent.putExtra(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.PREVIEWER_EDIT.value)
                intent.putExtra(NoteEditorFragment.EXTRA_EDIT_FROM_CARD_ID, cardId)
            }
        is NoteEditorDestination.AddNoteFromReviewer ->
            Intent(context, NoteEditorActivity::class.java).also { intent ->
                intent.putExtra(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.REVIEWER_ADD.value)
                animation?.let { intent.putExtra(AnkiActivity.FINISH_ANIMATION_EXTRA, it as Parcelable) }
            }
        is NoteEditorDestination.CopyNote ->
            Intent(context, NoteEditorActivity::class.java).also { intent ->
                intent.putExtra(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.NOTEEDITOR.value)
                intent.putExtra(NoteEditorFragment.EXTRA_DID, deckId)
                intent.putExtra(NoteEditorFragment.EXTRA_CONTENTS, fieldsText)
                tags?.let { intent.putExtra(NoteEditorFragment.EXTRA_TAGS, it.toTypedArray()) }
            }
        is NoteEditorDestination.PassArguments ->
            Intent(context, NoteEditorActivity::class.java).also { intent ->
                intent.putExtras(arguments)
                intent.action = action
            }
    }
