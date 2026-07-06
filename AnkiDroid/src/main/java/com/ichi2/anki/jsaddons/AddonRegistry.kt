// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.jsaddons

import timber.log.Timber
import java.net.URL

/**
 * A remote registry of installable addons: a JSON index at [indexUrl] containing an array of
 * addon manifests, each with a `dist.tarball` download URL. The index shape reuses the
 * dormant npm-style infrastructure ([getAddonModelListFromJson]).
 *
 * This is a **best guess** at distribution (see `docs/addons/design-notes.md` §2): the real
 * registry — AnkiWeb vs AnkiHub vs an Obsidian-style curated repo — is an ecosystem decision,
 * so [indexUrl] is injectable and defaults to a placeholder. The point is a working
 * fetch → list → install → update-check path that a real registry URL slots into.
 */
class AddonRegistry(
    private val indexUrl: URL = URL(DEFAULT_INDEX_URL),
) {
    sealed interface FetchResult {
        /** The index was fetched; [addons] are the valid entries (invalid ones skipped) */
        data class Success(
            val addons: List<AddonModel>,
        ) : FetchResult

        data class Failure(
            val message: String,
        ) : FetchResult
    }

    /** Fetches and parses the index; never throws (network/parse failures → [FetchResult.Failure]). */
    fun fetchAvailableAddons(): FetchResult =
        try {
            val (addons, errors) = getAddonModelListFromJson(indexUrl)
            if (errors.isNotEmpty()) Timber.i("Registry entries skipped as invalid:\n%s", errors)
            FetchResult.Success(addons)
        } catch (e: Exception) {
            Timber.w(e, "Failed to fetch the addon registry")
            FetchResult.Failure(e.localizedMessage ?: "Unable to reach the addon registry")
        }

    companion object {
        // placeholder: the real registry is undecided (design-notes §2)
        const val DEFAULT_INDEX_URL = "https://ankidroid.org/addons/index.json"
    }
}
