// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.NavigationDrawerActivity
import com.ichi2.anki.dialogs.ChangeNoteTypeDialog
import com.ichi2.anki.dialogs.FlagRenameDialog
import com.ichi2.anki.dialogs.GradeNowDialog
import com.ichi2.anki.dialogs.SimpleMessageDialog
import com.ichi2.anki.dialogs.tags.TagsDialog
import com.ichi2.anki.dialogs.tags.TagsDialogFactory
import com.ichi2.anki.dialogs.tags.TagsDialogListener
import com.ichi2.anki.export.ExportDialogFragment
import com.ichi2.anki.filtered.FilteredDeckOptionsFragment
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.NoteId
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.previewer.PreviewerFragment
import com.ichi2.anki.scheduling.ForgetCardsDialog
import com.ichi2.anki.scheduling.SetDueDateDialog
import com.ichi2.anki.updateBottomNavOnScroll
import com.ichi2.themes.Themes

internal object AnkiDroidBrowserIntegration : BrowserIntegration {
    override val isNightTheme get() = Themes.isNightTheme

    override fun isStandalone(activity: FragmentActivity?) = activity is CardBrowser

    override fun onNavigationPressed(activity: FragmentActivity) = (activity as NavigationDrawerActivity).onNavigationPressed()

    override fun updateBottomNavOnScroll(
        fragment: Fragment,
        dy: Int,
    ) = fragment.updateBottomNavOnScroll(dy)

    override fun attachTagsDialog(
        fragment: Fragment,
        listener: TagsDialogListener,
    ): BrowserTagsDialog {
        val factory = TagsDialogFactory(listener).attachToActivity<TagsDialogFactory>(fragment.requireActivity())
        return object : BrowserTagsDialog {
            override fun show(
                activity: FragmentActivity,
                filterByTag: Boolean,
                noteIds: List<NoteId>,
                checkedTags: ArrayList<String>,
            ) {
                factory.show(
                    activity,
                    type = if (filterByTag) TagsDialog.DialogType.FILTER_BY_TAG else TagsDialog.DialogType.EDIT_TAGS,
                    noteIds = noteIds,
                    checkedTags = checkedTags,
                )
            }
        }
    }

    override fun changeNoteTypeDialog(noteIds: List<NoteId>) = ChangeNoteTypeDialog.newInstance(noteIds)

    override fun gradeNow(
        activity: FragmentActivity,
        cardIds: List<CardId>,
    ) {
        GradeNowDialog.showDialog(activity as AnkiActivity, cardIds)
    }

    override fun setDueDate(
        activity: FragmentActivity,
        cardIds: List<CardId>,
    ) = SetDueDateDialog.show(activity, cardIds)

    override fun forgetCardsDialog() = ForgetCardsDialog()

    override fun exportDialog(
        cardsOrNotes: CardsOrNotes,
        ids: List<Long>,
    ) = ExportDialogFragment.newInstance(
        IdsFile.getDirectory(),
        if (cardsOrNotes == CardsOrNotes.CARDS) ExportDialogFragment.ExportType.Cards else ExportDialogFragment.ExportType.Notes,
        ids,
    )

    override fun previewIntent(
        context: Context,
        ids: IdsFile,
        currentIndex: Int,
    ) = PreviewerFragment.getIntent(context, ids, currentIndex)

    override fun filteredDeckIntent(
        context: Context,
        search: String,
    ) = FilteredDeckOptionsFragment.getIntent(context, search = search)

    override fun messageDialog(
        title: String,
        message: String,
    ) = SimpleMessageDialog.newInstance(title, message, reload = false)

    override fun renameFlagsDialog() = FlagRenameDialog()
}
