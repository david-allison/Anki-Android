/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.browser

import android.content.Context
import android.view.Menu
import android.view.SubMenu
import androidx.appcompat.widget.ThemeUtils
import androidx.lifecycle.viewModelScope
import com.google.android.material.search.SearchBar
import com.ichi2.anki.CardBrowser.Mode
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.Flag
import com.ichi2.anki.R
import com.ichi2.anki.browser.CardBrowserViewModel.MenuState
import com.ichi2.anki.model.CardsOrNotes.CARDS
import com.ichi2.anki.ui.internationalization.toSentenceCase
import kotlinx.coroutines.launch
import timber.log.Timber

fun MenuState.setup(
    searchBar: SearchBar,
    context: Context,
    viewModel: CardBrowserViewModel,
) {
    searchBar.menu?.clear()
    when (this) {
        is MenuState.Standard -> {
            Timber.d("setting up standard menu")
            searchBar.inflateMenu(R.menu.card_browser)
            setup(searchBar.menu, context, viewModel)
        }
        is MenuState.MultiSelect -> {
            Timber.d("setting up multi-select menu")
            searchBar.inflateMenu(R.menu.card_browser_multiselect)
            setup(searchBar.menu, context, viewModel)
        }
    }
}

private fun MenuState.Standard.setup(
    menu: Menu,
    context: Context,
    viewModel: CardBrowserViewModel,
) {
    Timber.d("setup standard menu")
    menu.findItem(R.id.action_create_filtered_deck)?.title = TR.qtMiscCreateFilteredDeck()

    menu.findItem(R.id.action_save_search)?.isVisible = this.saveSearchEnabled
    menu.findItem(R.id.action_list_my_searches)?.isVisible = this.openMySearchesEnabled
    menu.findItem(R.id.action_select_all)?.isVisible = this.selectAllEnabled
    menu.findItem(R.id.action_undo)?.isVisible = this.undoEnabled
    menu.findItem(R.id.action_preview)?.isVisible = this.previewEnabled
    menu.findItem(R.id.action_find_replace)?.isVisible = this.findReplaceEnabled
    menu.findItem(R.id.action_find_replace)?.title = TR.browsingFindAndReplace().toSentenceCase(context, R.string.sentence_find_and_replace)

    menu.findItem(R.id.action_search_by_flag)?.subMenu?.let { subMenu ->
        viewModel.setupFlags(context, subMenu, Mode.SINGLE_SELECT)
    }
}

private fun MenuState.MultiSelect.setup(
    menu: Menu,
    context: Context,
    viewModel: CardBrowserViewModel,
) {
    Timber.d("setup multiselect menu")
    menu.findItem(R.id.action_reschedule_cards)?.title = TR.actionsSetDueDate().toSentenceCase(context, R.string.sentence_set_due_date)
    menu.findItem(R.id.action_suspend_card)?.title = TR.browsingToggleSuspend().toSentenceCase(context, R.string.sentence_toggle_suspend)
    menu.findItem(R.id.action_toggle_bury)?.title = TR.browsingToggleBury().toSentenceCase(context, R.string.sentence_toggle_bury)
    menu.findItem(R.id.action_mark_card)?.title = TR.browsingToggleMark()

    menu.findItem(R.id.action_undo)?.isVisible = this.undoEnabled
    menu.findItem(R.id.action_preview)?.isVisible = this.previewEnabled
    menu.findItem(R.id.action_view_card_info)?.isVisible = this.cardInfoEnabled
    menu.findItem(R.id.action_edit_note)?.isVisible = this.canEditNote
    menu.findItem(R.id.action_find_replace)?.isVisible = this.findReplaceEnabled
    menu.findItem(R.id.action_find_replace)?.title = TR.browsingFindAndReplace().toSentenceCase(context, R.string.sentence_find_and_replace)
    menu.findItem(R.id.action_grade_now)?.title = TR.actionsGradeNow().toSentenceCase(context, R.string.sentence_grade_now)

    menu.findItem(R.id.action_flag)?.subMenu?.let { subMenu ->
        viewModel.setupFlags(context, subMenu, Mode.MULTI_SELECT)
    }

    menu.findItem(R.id.action_export_selected)?.apply {
        this.title =
            if (viewModel.cardsOrNotes == CARDS) {
                context.resources.getQuantityString(
                    R.plurals.card_browser_export_cards,
                    viewModel.selectedRowCount(),
                )
            } else {
                context.resources.getQuantityString(
                    R.plurals.card_browser_export_notes,
                    viewModel.selectedRowCount(),
                )
            }
    }

    // TODO: offload this to the ViewModel
    viewModel.viewModelScope.launch {
        menu.findItem(R.id.action_delete_card)?.apply {
            this.title =
                context.resources.getQuantityString(
                    R.plurals.card_browser_delete_notes,
                    viewModel.selectedNoteCount(),
                )
        }
    }
}

private fun CardBrowserViewModel.setupFlags(
    context: Context,
    subMenu: SubMenu,
    mode: Mode,
) {
    viewModelScope.launch {
        val groupId =
            when (mode) {
                Mode.SINGLE_SELECT -> mode.value
                Mode.MULTI_SELECT -> mode.value
            }

        subMenu.clear()
        for ((flag, displayName) in Flag.queryDisplayNames()) {
            val item =
                subMenu
                    .add(groupId, flag.code, Menu.NONE, displayName)
                    .setIcon(flag.drawableRes)
            if (flag == Flag.NONE) {
                val color = ThemeUtils.getThemeAttrColor(context, android.R.attr.colorControlNormal)
                item.icon?.mutate()?.setTint(color)
            }
        }
    }
}
