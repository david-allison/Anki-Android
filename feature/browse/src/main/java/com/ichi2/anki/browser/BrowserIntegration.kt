// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import android.content.Context
import android.content.Intent
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.ichi2.anki.dialogs.tags.TagsDialogListener
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.NoteId
import com.ichi2.anki.model.CardsOrNotes

/** Operations owned by the application or by another screen, supplied at application startup. */
interface BrowserIntegration {
    val isNightTheme: Boolean

    fun isStandalone(activity: FragmentActivity?): Boolean

    fun onNavigationPressed(activity: FragmentActivity)

    fun updateBottomNavOnScroll(
        fragment: Fragment,
        dy: Int,
    )

    fun attachTagsDialog(
        fragment: Fragment,
        listener: TagsDialogListener,
    ): BrowserTagsDialog

    fun changeNoteTypeDialog(noteIds: List<NoteId>): DialogFragment

    fun gradeNow(
        activity: FragmentActivity,
        cardIds: List<CardId>,
    )

    fun setDueDate(
        activity: FragmentActivity,
        cardIds: List<CardId>,
    )

    fun forgetCardsDialog(): DialogFragment

    fun exportDialog(
        cardsOrNotes: CardsOrNotes,
        ids: List<Long>,
    ): DialogFragment

    fun previewIntent(
        context: Context,
        ids: IdsFile,
        currentIndex: Int,
    ): Intent

    fun filteredDeckIntent(
        context: Context,
        search: String,
    ): Intent

    fun messageDialog(
        title: String,
        message: String,
    ): DialogFragment

    fun renameFlagsDialog(): DialogFragment

    companion object {
        lateinit var current: BrowserIntegration
    }
}

interface BrowserTagsDialog {
    fun show(
        activity: FragmentActivity,
        filterByTag: Boolean = false,
        noteIds: List<NoteId> = emptyList(),
        checkedTags: ArrayList<String> = arrayListOf(),
    )
}
