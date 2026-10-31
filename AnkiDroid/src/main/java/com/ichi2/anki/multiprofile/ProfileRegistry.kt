// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.multiprofile

import android.content.SharedPreferences
import androidx.core.content.edit
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.multiprofile.ProfileManager.Companion.PREF_LAST_ACTIVE_PROFILE_ID
import com.ichi2.anki.multiprofile.ProfileManager.Companion.PREF_WEBVIEW_PROFILE_ID
import com.ichi2.anki.multiprofile.ProfileManager.ProfileMetadata
import timber.log.Timber

/** Profile metadata and naming policies, independent of profile activation and Android services. */
class ProfileRegistry(
    private val globalPrefs: SharedPreferences,
) {
    fun createNewProfile(displayName: ProfileName): ProfileId {
        require(!isNameTaken(displayName)) { "Profile name is already in use" }
        val newProfileId = generateUniqueProfileId()

        val metadata = ProfileMetadata(displayName = displayName)

        saveProfile(newProfileId, metadata)

        Timber.i("Created new profile: ${newProfileId.value}")
        return newProfileId
    }

    private fun isNameTaken(
        name: ProfileName,
        ignoring: ProfileId? = null,
    ): Boolean = name.existsIn(getAllProfiles().filterKeys { it != ignoring }.values.map { it.displayName.value })

    /**
     * Generates a unique [ProfileId] that does not collide with existing profiles.
     *
     * @return A unique [ProfileId] not present in the [ProfileRegistry].
     *
     * @throws IllegalStateException if a unique ID cannot be generated after
     * [MAX_ATTEMPTS] attempts.
     */
    private fun generateUniqueProfileId(): ProfileId {
        var newId: ProfileId
        var collisionCount = 0

        do {
            if (collisionCount >= MAX_ATTEMPTS) {
                // If we hit this, something is critically wrong with our registry
                throw IllegalStateException("Failed to generate a unique Profile ID after $MAX_ATTEMPTS attempts.")
            }

            if (collisionCount == 1) {
                val warningMessage = "Profile ID collision detected! Retrying generation..."
                val silentException = IllegalStateException(warningMessage)

                CrashReportService.sendExceptionReport(silentException, "ProfileManager::generateUniqueProfileId")
                Timber.w(silentException, warningMessage)
            } else if (collisionCount > 1) {
                Timber.w("Profile ID collision still occurring. Retrying... (Attempt ${collisionCount + 1})")
            }

            newId = ProfileId.generate()
            collisionCount++
        } while (contains(newId))

        return newId
    }

    fun renameProfile(
        profileId: ProfileId,
        newDisplayName: ProfileName,
    ) {
        Timber.d("ProfileManager::renameProfile called for $profileId")

        val existing =
            getProfileMetadata(profileId)
                ?: throw IllegalArgumentException("Profile $profileId not found")

        if (existing.displayName == newDisplayName) {
            Timber.d("Rename skipped: New name matches existing name for $profileId")
            return
        }

        require(!isNameTaken(newDisplayName, ignoring = profileId)) { "Profile name is already in use" }

        val updated = existing.copy(displayName = newDisplayName)
        saveProfile(profileId, updated)

        Timber.d("Renamed profile $profileId to '$newDisplayName'")
    }

    internal fun getLastActiveProfileId(): ProfileId? {
        val id = globalPrefs.getString(PREF_LAST_ACTIVE_PROFILE_ID, null)
        return id?.let { ProfileId(it) }
    }

    internal fun setLastActiveProfileId(
        id: ProfileId,
        commit: Boolean = false,
    ) {
        globalPrefs.edit(commit = commit) { putString(PREF_LAST_ACTIVE_PROFILE_ID, id.value) }
    }

    internal fun getWebViewProfileId(): ProfileId? {
        val id = globalPrefs.getString(PREF_WEBVIEW_PROFILE_ID, null)
        return id?.let { ProfileId(it) }
    }

    internal fun setWebViewProfileId(id: ProfileId) {
        globalPrefs.edit { putString(PREF_WEBVIEW_PROFILE_ID, id.value) }
    }

    /**
     * Whether [id] is the only profile in the registry.
     *
     * Counts raw keys rather than [getAllProfiles] so that an entry which fails to parse still
     * counts as a profile.
     */
    internal fun isOnlyProfile(id: ProfileId): Boolean {
        val profileKeys = globalPrefs.all.keys - BOOKKEEPING_KEYS
        return profileKeys.size == 1 && id.value in profileKeys
    }

    internal fun saveProfile(
        id: ProfileId,
        metadata: ProfileMetadata,
        isActive: Boolean = false,
    ) {
        globalPrefs.edit {
            putString(id.value, metadata.toJson())
            if (isActive) {
                putString(PREF_LAST_ACTIVE_PROFILE_ID, id.value)
            }
        }
    }

    private fun getProfileMetadata(id: ProfileId): ProfileMetadata? {
        val jsonString = globalPrefs.getString(id.value, null) ?: return null
        return try {
            ProfileMetadata.fromJson(jsonString)
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse profile metadata for ${id.value}")
            null
        }
    }

    /**
     * Retrieves all registered profiles from the global SharedPreferences.
     *
     * Iterates over all stored entries, skipping the
     * [PREF_LAST_ACTIVE_PROFILE_ID] metadata key, and deserializes each
     * value into a [ProfileMetadata] instance. Entries that fail to parse
     * are logged and silently skipped.
     *
     * @return A map of [ProfileId] to [ProfileMetadata] for every
     *   successfully parsed profile in the registry.
     */
    fun getAllProfiles(): Map<ProfileId, ProfileMetadata> {
        val result = mutableMapOf<ProfileId, ProfileMetadata>()
        val allEntries = globalPrefs.all
        for ((key, value) in allEntries) {
            // Skip internal bookkeeping keys; only profile entries remain
            if (key in BOOKKEEPING_KEYS) continue

            val metadata =
                try {
                    ProfileMetadata.fromJson(value as String)
                } catch (e: Exception) {
                    Timber.w(e, "Skipping corrupt profile entry: $key")
                    continue
                }
            result[ProfileId(key)] = metadata
        }
        return result
    }

    /**
     * Removes a profile entry from the registry.
     *
     * Does **not** delete the profile's data directory on disk.
     * Callers are responsible for cleaning up file-system resources.
     *
     * @param id The [ProfileId] of the profile to remove.
     */
    internal fun removeProfile(id: ProfileId) {
        globalPrefs.edit { remove(id.value) }
    }

    private fun contains(id: ProfileId): Boolean = globalPrefs.contains(id.value)

    companion object {
        private const val MAX_ATTEMPTS = 10
        private val BOOKKEEPING_KEYS = setOf(PREF_LAST_ACTIVE_PROFILE_ID, PREF_WEBVIEW_PROFILE_ID)
    }
}
