// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.preferences.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ichi2.anki.multiprofile.ProfileManager
import com.ichi2.anki.multiprofile.ProfileName
import com.ichi2.anki.multiprofile.ProfileRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

/**
 * State holder for [SwitchProfilesFragment]. Keeps the dialog visibility out
 * of the view layer so it survives configuration changes.
 */
class SwitchProfilesViewModel(
    private val profileRegistry: ProfileRegistry,
) : ViewModel() {
    constructor(profileManager: ProfileManager) : this(profileManager.profileRegistry)

    /** Profiles shown in the list. */
    val profiles: StateFlow<List<ProfileItem>>
        field = MutableStateFlow(profileRegistry.profileItems())

    val isAddProfileDialogVisible: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val profileBeingRenamed: StateFlow<ProfileItem?>
        field = MutableStateFlow<ProfileItem?>(null)

    fun showAddProfileDialog() {
        isAddProfileDialogVisible.value = true
    }

    fun dismissAddProfileDialog() {
        isAddProfileDialogVisible.value = false
    }

    /** Called when the user confirms a valid name in the add-profile dialog. */
    fun addProfile(name: ProfileName) {
        isAddProfileDialogVisible.value = false
        Timber.i("Add profile confirmed (%d chars)", name.value.length)
        profileRegistry.createNewProfile(name)
        refresh()
    }

    fun showRenameProfileDialog(profile: ProfileItem) {
        profileBeingRenamed.value = profile
    }

    fun dismissRenameProfileDialog() {
        profileBeingRenamed.value = null
    }

    fun renameProfile(name: ProfileName) {
        val profile = profileBeingRenamed.value ?: return
        profileBeingRenamed.value = null
        Timber.i("Rename profile confirmed: %s", profile.id)
        profileRegistry.renameProfile(profile.id, name)
        refresh()
    }

    fun deleteProfile(profile: ProfileItem) {
        Timber.i("Delete profile requested: %s", profile.id)
        // TODO: implement profile deletion via ProfileManager
    }

    /** Reloads the list from the registry. */
    fun refresh() {
        profiles.value = profileRegistry.profileItems()
    }

    companion object {
        fun factory(profileManager: ProfileManager): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    SwitchProfilesViewModel(profileManager)
                }
            }

        /**
         * The registry is backed by SharedPreferences, which has no defined order,
         * so sort by name to keep the list stable across launches.
         */
        private fun ProfileRegistry.profileItems(): List<ProfileItem> =
            getAllProfiles()
                .map { (id, metadata) -> ProfileItem(id = id, name = metadata.displayName.value) }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }
}
