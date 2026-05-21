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
import android.os.Parcelable
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.NoteEditorActivity
import com.ichi2.anki.NoteEditorFragment
import com.ichi2.anki.NoteEditorFragment.Companion.NoteEditorCaller
import com.ichi2.anki.common.destinations.NoteEditorDestination

/** Resolves a [NoteEditorDestination] to its launch [Intent] for [NoteEditorActivity]. */
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
        is NoteEditorDestination.AddNoteFromCardBrowser ->
            Intent(context, NoteEditorActivity::class.java).also { intent ->
                intent.putExtra(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.CARDBROWSER_ADD.value)
                intent.putExtra(NoteEditorFragment.EXTRA_TEXT_FROM_SEARCH_VIEW, searchTerms)
                intent.putExtra(NoteEditorFragment.IN_CARD_BROWSER_ACTIVITY, false)
                lastDeckId?.takeIf { it > 0 }?.let { intent.putExtra(NoteEditorFragment.EXTRA_DID, it) }
            }
        is NoteEditorDestination.EditSelection ->
            Intent(context, NoteEditorActivity::class.java).also { intent ->
                intent.putExtra(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.EDIT.value)
                // To handle single card selection
                intent.putExtra(NoteEditorFragment.EXTRA_CARD_ID, cardIds.first())
                // To handle multi select and note edit
                intent.putExtra(NoteEditorFragment.EXTRA_CARD_IDS, cardIds.toLongArray())
                intent.putExtra(AnkiActivity.FINISH_ANIMATION_EXTRA, animation as Parcelable)
                intent.putExtra(NoteEditorFragment.IN_CARD_BROWSER_ACTIVITY, inCardBrowserActivity)
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
