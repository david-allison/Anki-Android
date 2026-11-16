// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import androidx.fragment.app.FragmentActivity
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.CommonString
import com.ichi2.anki.browser.CardBrowserViewModel
import com.ichi2.anki.browser.SaveSearchResult
import com.ichi2.anki.browser.search.SavedSearch
import com.ichi2.anki.dialogs.SaveBrowserSearchDialogFragment.Companion.RESULT_SEARCH_QUERY
import com.ichi2.anki.dialogs.SaveBrowserSearchDialogFragment.Companion.RESULT_SEARCH_QUERY_NAME
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.snackbar.showSnackbar

fun FragmentActivity.registerSaveSearchHandler(
    viewModel: CardBrowserViewModel,
    updateAfterUserSearchIsSaved: (SaveSearchResult) -> Unit,
) {
    supportFragmentManager.setFragmentResultListener(
        SaveBrowserSearchDialogFragment.REQUEST_SAVE_SEARCH,
        this,
    ) { _, bundle ->
        val savedSearchName =
            bundle.getString(RESULT_SEARCH_QUERY_NAME)
        if (savedSearchName.isNullOrEmpty()) {
            showSnackbar(
                CommonString.card_browser_list_my_searches_new_search_error_empty_name,
                Snackbar.LENGTH_SHORT,
            )
            return@setFragmentResultListener
        }
        val toSave =
            SavedSearch(
                name = savedSearchName,
                query = bundle.getString(RESULT_SEARCH_QUERY) ?: return@setFragmentResultListener,
            )

        launchCatchingTask {
            val saveStatus = viewModel.saveSearch(toSave)
            updateAfterUserSearchIsSaved(saveStatus)
        }
    }
}
