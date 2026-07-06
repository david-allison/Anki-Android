// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.jsaddons

import android.content.Context
import com.ichi2.anki.jsaddons.AddonsConst.REVIEWER_ADDON
import com.ichi2.anki.settings.Prefs
import timber.log.Timber

/**
 * Scripts of the enabled reviewer addons, for injection into the new study screen.
 *
 * Two delivery mechanisms are provided:
 *
 * 1. [addonScriptUrls] (wired up): URLs for `<script src>` tags, served through
 *    [AddonStorage.resolveWebExport] by the WebView's resource interception. Scripts
 *    participate in normal page load ordering (after AnkiDroid's own scripts), appear
 *    under their real URL in devtools, and reload naturally with the page.
 * 2. [addonScriptsForEvaluation] (alternative): raw script text for
 *    `WebView.evaluateJavascript`. Needs no serving infrastructure, but runs after page
 *    load rather than during it, must be re-evaluated manually on reload, and errors
 *    attribute to an anonymous script (mitigated here with a `sourceURL` comment).
 */
object AddonReviewerScripts {
    /** The enabled, valid, reviewer-type addons; empty unless [Prefs.devAddonsEnabled] */
    fun enabledReviewerAddons(context: Context): List<AddonModel> {
        if (!Prefs.devAddonsEnabled) return emptyList()
        val stateStore = AddonStateStore(context)
        return AddonStorage(context)
            .getInstalledAddons()
            .mapNotNull { (it.result as? AddonValidationResult.Valid)?.addonModel }
            .filter { it.addonType == REVIEWER_ADDON && stateStore.isEnabled(it.name) }
    }

    /** Implementation 1: URLs for `<script src>` tags, served by [AddonStorage.resolveWebExport] */
    fun addonScriptUrls(context: Context): List<String> = enabledReviewerAddons(context).mapNotNull { addon -> urlFor(addon) }

    /** Implementation 2: script contents for `WebView.evaluateJavascript` */
    fun addonScriptsForEvaluation(context: Context): List<String> {
        val storage = AddonStorage(context)
        return enabledReviewerAddons(context).mapNotNull { addon ->
            val url = urlFor(addon) ?: return@mapNotNull null
            val script = storage.resolveWebExport(url) ?: return@mapNotNull null
            // sourceURL attributes errors and breakpoints to the addon in devtools
            script.readText() + "\n//# sourceURL=$url"
        }
    }

    private fun urlFor(addon: AddonModel): String? {
        // a hostile 'main' could otherwise build a URL escaping the addon's directory
        if (addon.main.startsWith("/") || ".." in addon.main) {
            Timber.w("Skipping addon '%s': unsafe 'main' entry '%s'", addon.name, addon.main)
            return null
        }
        return "${AddonStorage.WEB_EXPORTS_PREFIX}${addon.name}/${addon.main}"
    }
}
