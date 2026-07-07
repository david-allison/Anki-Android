// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.jsaddons

import android.content.Context
import android.view.Menu
import com.ichi2.anki.settings.Prefs
import timber.log.Timber

/**
 * Renders addon menu contributions into native screens' menus.
 *
 * A native screen calls [populate] from its `onCreateOptionsMenu`/`onCreateMenu`; each
 * contributed item gets its own click listener that dispatches to the addon's background
 * context ([AddonBackgroundHost.fireMenuClick]). One call is all a screen needs — the
 * selection, dev-flag gating and dispatch live here (see `docs/addons/design-notes.md` §4).
 */
object AddonMenus {
    /** Native screen ids used in the manifest `menus[].screen` field */
    const val DECK_PICKER = "deck-picker"

    /** The enabled addons' contributions for [screenId] (empty unless the dev flag is on) */
    fun contributionsForScreen(
        context: Context,
        screenId: String,
    ): List<AddonMenuContribution> {
        if (!Prefs.devAddonsEnabled) return emptyList()
        val stateStore = AddonStateStore(context)
        return AddonStorage(context)
            .getInstalledAddons()
            .mapNotNull { (it.result as? AddonValidationResult.Valid)?.addonModel }
            .filter { stateStore.isEnabled(it.name) }
            .flatMap { it.menuContributions() }
            .filter { it.screen == screenId }
    }

    /**
     * Adds the enabled addons' [screenId] contributions to [menu]. Each item dispatches to
     * its addon's background context on click. Returns the number of items added.
     */
    fun populate(
        menu: Menu,
        context: Context,
        screenId: String,
        dispatch: (AddonMenuContribution) -> Unit = ::dispatchToBackground,
    ): Int {
        val contributions = contributionsForScreen(context, screenId)
        for (contribution in contributions) {
            menu.add(contribution.title).setOnMenuItemClickListener {
                Timber.i("Addon menu '%s' clicked (addon '%s')", contribution.id, contribution.addonName)
                dispatch(contribution)
                true
            }
        }
        return contributions.size
    }

    private fun dispatchToBackground(contribution: AddonMenuContribution) {
        val host = AddonBackgroundHost.current
        if (host == null) {
            Timber.w("Addon '%s' menu click ignored: no running background context", contribution.addonName)
            return
        }
        host.fireMenuClick(contribution.addonName, contribution.id)
    }
}
