// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.multiprofile

import timber.log.Timber

/** Selects isolated WebView storage, or records ownership after shared cookies have been cleared. */
internal class ProfileBrowserStorage(
    private val registry: ProfileRegistry,
    private val supportsSeparateDirectories: Boolean,
    private val setDirectorySuffix: (String) -> Unit,
    private val clearCookies: (onComplete: () -> Unit) -> Unit,
) {
    fun configure(profileId: ProfileId) {
        if (supportsSeparateDirectories) {
            if (profileId.isDefault()) return
            try {
                setDirectorySuffix(profileId.value)
            } catch (e: Exception) {
                Timber.w(e, "Failed to set WebView directory suffix (WebView already initialized?)")
            }
            return
        }

        val owner = registry.getWebViewProfileId()
        if (owner == profileId) return

        if (owner == null && profileId.isDefault() && registry.isOnlyProfile(profileId)) {
            // Preserve the existing AnkiWeb session for users who have never switched profiles.
            Timber.i("Adopting the existing WebView data: no other profile has run on this install")
            registry.setWebViewProfileId(profileId)
            return
        }

        Timber.i("WebView data belongs to another profile, clearing cookies")
        clearCookies { registry.setWebViewProfileId(profileId) }
    }
}
