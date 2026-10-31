// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.preferences.profiles

import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.ichi2.anki.multiprofile.ProfileId
import com.ichi2.anki.multiprofile.ProfileManager
import com.ichi2.anki.multiprofile.ProfileName
import com.ichi2.anki.multiprofile.ProfileName.ValidationResult
import com.ichi2.anki.multiprofile.ProfileRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SwitchProfilesViewModelTest {
    private lateinit var profileRegistry: ProfileRegistry

    @Before
    fun setUp() {
        profileRegistry = ProfileRegistry(SPMockBuilder().createSharedPreferences())
        profileRegistry.saveProfile(
            ProfileId.DEFAULT,
            ProfileManager.ProfileMetadata(ProfileName.fromTrustedSource(ProfileManager.DEFAULT_PROFILE_DISPLAY_NAME)),
            isActive = true,
        )
    }

    private fun viewModel() = SwitchProfilesViewModel(profileRegistry)

    private val validName: ProfileName
        get() = (ProfileName.validate("Work") as ValidationResult.Valid).name

    private fun createProfile(name: String) = profileRegistry.createNewProfile(nameOf(name))

    private fun nameOf(raw: String) = (ProfileName.validate(raw) as ValidationResult.Valid).name

    @Test
    fun `add-profile dialog is hidden initially`() {
        assertFalse(viewModel().isAddProfileDialogVisible.value)
    }

    @Test
    fun `showAddProfileDialog makes the dialog visible`() {
        val viewModel = viewModel()
        viewModel.showAddProfileDialog()
        assertTrue(viewModel.isAddProfileDialogVisible.value)
    }

    @Test
    fun `dismissAddProfileDialog hides the dialog`() {
        val viewModel = viewModel()
        viewModel.showAddProfileDialog()
        viewModel.dismissAddProfileDialog()
        assertFalse(viewModel.isAddProfileDialogVisible.value)
    }

    @Test
    fun `addProfile hides the dialog`() {
        val viewModel = viewModel()
        viewModel.showAddProfileDialog()
        viewModel.addProfile(validName)
        assertFalse(viewModel.isAddProfileDialogVisible.value)
    }

    @Test
    fun `addProfile lists the new profile`() {
        val viewModel = viewModel()

        viewModel.addProfile(nameOf("Work"))

        assertEquals(listOf("Default", "Work"), viewModel.profiles.value.map { it.name })
    }

    @Test
    fun `showRenameProfileDialog opens the dialog for that profile`() {
        val viewModel = viewModel()
        val default = viewModel.profiles.value.single()

        viewModel.showRenameProfileDialog(default)

        assertEquals(default, viewModel.profileBeingRenamed.value)
    }

    @Test
    fun `dismissRenameProfileDialog hides the dialog`() {
        val viewModel = viewModel()
        viewModel.showRenameProfileDialog(viewModel.profiles.value.single())

        viewModel.dismissRenameProfileDialog()

        assertNull(viewModel.profileBeingRenamed.value)
    }

    @Test
    fun `renameProfile lists the new name and closes the dialog`() {
        createProfile("Work")
        val viewModel = viewModel()
        viewModel.showRenameProfileDialog(viewModel.profiles.value.single { it.name == "Work" })

        viewModel.renameProfile(nameOf("Office"))

        assertEquals(listOf("Default", "Office"), viewModel.profiles.value.map { it.name })
        assertNull(viewModel.profileBeingRenamed.value)
    }

    @Test
    fun `the list starts with the Default profile`() {
        assertEquals(listOf(ProfileId.DEFAULT), viewModel().profiles.value.map { it.id })
    }

    @Test
    fun `every registered profile is listed`() {
        val work = createProfile("Work")

        val listed = viewModel().profiles.value.map { it.id }

        assertEquals(setOf(ProfileId.DEFAULT, work), listed.toSet())
    }

    @Test
    fun `profiles are listed by name so the order is stable`() {
        createProfile("Zebra")
        createProfile("Alpha")

        val names = viewModel().profiles.value.map { it.name }

        assertEquals(listOf("Alpha", "Default", "Zebra"), names)
    }

    @Test
    fun `profiles are listed by name ignoring case`() {
        createProfile("bravo")
        createProfile("Alpha")

        val names = viewModel().profiles.value.map { it.name }

        assertEquals(listOf("Alpha", "bravo", "Default"), names)
    }

    @Test
    fun `refresh picks up a profile added after the view model was created`() {
        val viewModel = viewModel()
        createProfile("Later")

        assertEquals(1, viewModel.profiles.value.size, "not visible until refreshed")
        viewModel.refresh()

        assertEquals(listOf("Default", "Later"), viewModel.profiles.value.map { it.name })
    }
}
