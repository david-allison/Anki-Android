// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.jsaddons

import com.github.zafarkhaja.semver.Version
import com.ichi2.anki.web.HttpFetcher
import okhttp3.Request
import timber.log.Timber
import java.io.File
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

    /**
     * Downloads [addon]'s tarball and installs it through the atomic [AddonStorage.installFromTarball],
     * so registry install and file install share one code path and its guarantees.
     *
     * @return the install result, or [AddonValidationResult.Invalid] if the download failed
     */
    fun install(
        addon: AddonModel,
        storage: AddonStorage,
    ): AddonValidationResult {
        val tarballUrl = addon.dist?.tarball ?: return AddonValidationResult.Invalid(listOf("Registry entry has no download URL"))
        val tempTarball = File.createTempFile("addon-download", ".tgz")
        return try {
            downloadTo(tarballUrl, tempTarball)
            storage.installFromTarball(tempTarball)
        } catch (e: Exception) {
            Timber.w(e, "Failed to download addon '%s'", addon.name)
            AddonValidationResult.Invalid(listOf("Unable to download the addon: ${e.localizedMessage}"))
        } finally {
            tempTarball.delete()
        }
    }

    private fun downloadTo(
        url: String,
        dest: File,
    ) {
        val client = HttpFetcher.getOkHttpBuilder(fakeUserAgent = false).build()
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("HTTP ${response.code}")
            dest.outputStream().use { out -> response.body.byteStream().copyTo(out) }
        }
    }

    companion object {
        // placeholder: the real registry is undecided (design-notes §2)
        const val DEFAULT_INDEX_URL = "https://ankidroid.org/addons/index.json"
    }
}

/** An installed addon for which the registry offers a newer version */
data class AddonUpdate(
    val name: String,
    val installedVersion: String,
    val availableVersion: String,
)

/**
 * The updates available: registry entries whose version is strictly newer (semver) than the
 * installed one. Unparseable versions are skipped rather than treated as updates.
 *
 * @param available the registry's addons
 * @param installedVersions installed addon name → version
 */
fun computeAvailableUpdates(
    available: List<AddonModel>,
    installedVersions: Map<String, String>,
): List<AddonUpdate> =
    available.mapNotNull { addon ->
        val installed = installedVersions[addon.name] ?: return@mapNotNull null
        val isNewer =
            try {
                Version.parse(addon.version).isHigherThan(Version.parse(installed))
            } catch (_: Exception) {
                false
            }
        if (isNewer) AddonUpdate(addon.name, installed, addon.version) else null
    }
