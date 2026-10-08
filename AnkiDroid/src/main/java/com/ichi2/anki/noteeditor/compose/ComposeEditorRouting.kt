// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.ichi2.anki.NoteEditorFragment
import com.ichi2.anki.NoteEditorFragment.Companion.NoteEditorCaller
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.settings.Prefs

/** Image Occlusion and image-share entry points keep their dedicated editor flow. */
internal fun Intent.useComposeEditor(context: Context): Intent =
    apply {
        if (shouldUseComposeEditor()) setClass(context, ComposeNoteEditorActivity::class.java)
    }

internal fun Intent.shouldUseComposeEditor(): Boolean {
    if (!Prefs.isComposeNoteEditorEnabled || getBooleanExtra(ComposeNoteEditorActivity.EXTRA_USE_LEGACY, false)) return false
    // Its specialized multi-entry parser remains in the legacy editor.
    if (getStringExtra(Intent.EXTRA_SUBJECT) == "Aedict Notepad") return false
    return getIntExtra(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.DECKPICKER.value) !in
        setOf(NoteEditorCaller.IMG_OCCLUSION.value, NoteEditorCaller.ADD_IMAGE.value)
}

/** The note type is only available after opening the collection, so resolve this before loading UI. */
internal fun Collection.requiresLegacyEditor(arguments: Bundle): Boolean {
    val cardId =
        when (arguments.getInt(NoteEditorFragment.EXTRA_CALLER)) {
            NoteEditorCaller.EDIT.value -> arguments.getLong(NoteEditorFragment.EXTRA_CARD_ID)
            NoteEditorCaller.PREVIEWER_EDIT.value -> arguments.getLong(NoteEditorFragment.EXTRA_EDIT_FROM_CARD_ID)
            else -> null
        }
    return if (cardId != null) {
        getCard(cardId).note(this).notetype.isImageOcclusion
    } else {
        notetypes.get(defaultsForAdding().notetypeId)?.isImageOcclusion == true
    }
}
