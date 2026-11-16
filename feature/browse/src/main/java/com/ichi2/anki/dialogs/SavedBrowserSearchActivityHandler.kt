// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import androidx.fragment.app.FragmentActivity
import com.ichi2.anki.dialogs.SavedBrowserSearchesDialogFragment.Companion.RESULT_SAVED_SEARCH
import timber.log.Timber

fun FragmentActivity.registerSavedSearchActionHandler(action: (Int, String?) -> Unit) {
    supportFragmentManager.setFragmentResultListener(
        SavedBrowserSearchesDialogFragment.REQUEST_SAVED_SEARCH_ACTION,
        this,
    ) { _, bundle ->
        val type = bundle.getInt(SavedBrowserSearchesDialogFragment.RESULT_SAVED_SEARCH_ACTION)
        val searchName = bundle.getString(RESULT_SAVED_SEARCH)
        Timber.d("On user saved search selection named: %s", searchName)
        action(type, searchName)
    }
}
