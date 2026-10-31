// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import android.content.SharedPreferences
import androidx.core.content.edit
import com.ichi2.anki.model.CardsOrNotes

fun BrowserColumnCollection.Companion.load(
    prefs: SharedPreferences,
    mode: CardsOrNotes,
): BrowserColumnCollection = load(SharedPreferencesBrowserColumns(prefs), mode)

fun BrowserColumnCollection.Companion.replace(
    prefs: SharedPreferences,
    mode: CardsOrNotes,
    newColumns: List<CardBrowserColumn>,
) = replace(SharedPreferencesBrowserColumns(prefs), mode, newColumns)

fun BrowserColumnCollection.Companion.update(
    prefs: SharedPreferences,
    mode: CardsOrNotes,
    block: (MutableList<CardBrowserColumn?>) -> Boolean,
): BrowserColumnCollection? = update(SharedPreferencesBrowserColumns(prefs), mode, block)

fun BrowserColumnCollection.Companion.save(
    prefs: SharedPreferences,
    mode: CardsOrNotes,
    value: BrowserColumnCollection,
) = save(SharedPreferencesBrowserColumns(prefs), mode, value)

private class SharedPreferencesBrowserColumns(
    private val preferences: SharedPreferences,
) : BrowserColumnPreferences {
    override fun getString(
        key: String,
        defaultValue: String,
    ): String? = preferences.getString(key, defaultValue)

    override fun putString(
        key: String,
        value: String,
    ) {
        preferences.edit { putString(key, value) }
    }
}
