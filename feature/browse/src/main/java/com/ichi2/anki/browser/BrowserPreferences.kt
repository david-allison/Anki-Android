// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import com.ichi2.anki.common.android.appContext
import com.ichi2.anki.common.preferences.SharedPreferencesTestHook
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.browse.R as BrowseR

fun browserSharedPrefs() = SharedPreferencesTestHook.override ?: appContext.sharedPrefs()

/** Browser preferences use the active profile's preference file and retain their existing keys. */
object BrowserPreferences {
    @VisibleForTesting
    var resourcesTestingOverride: Resources? = null

    fun key(
        @StringRes resource: Int,
    ): String = (resourcesTestingOverride ?: appContext.resources).getString(resource)

    val devIsCardBrowserFragmented: Boolean
        get() = browserSharedPrefs().getBoolean(key(BrowseR.string.dev_card_browser_fragmented), false)

    val devUsingCardBrowserSearchView: Boolean
        get() = browserSharedPrefs().getBoolean(key(BrowseR.string.dev_card_browser_search_view), false)

    val devUsingCardBrowserSearchTools: Boolean
        get() = browserSharedPrefs().getBoolean(key(BrowseR.string.dev_card_browser_search_tools), false)

    var cardBrowserNoSorting: Boolean
        get() = browserSharedPrefs().getBoolean(key(BrowseR.string.pref_browser_no_sorting), false)
        set(value) {
            browserSharedPrefs().edit { putBoolean(key(BrowseR.string.pref_browser_no_sorting), value) }
        }
}
