// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.multiprofile

import androidx.core.content.edit
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.ichi2.anki.multiprofile.ProfileManager.Companion.PREF_WEBVIEW_PROFILE_ID
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertThrows
import timber.log.Timber
import java.util.UUID

class ProfileRegistryTest {
    private val prefs = SPMockBuilder().createSharedPreferences()

    @Before
    fun setUp() {
        registry().saveProfile(
            ProfileId.DEFAULT,
            ProfileManager.ProfileMetadata(ProfileName.fromTrustedSource(ProfileManager.DEFAULT_PROFILE_DISPLAY_NAME)),
            isActive = true,
        )
    }

    @After
    fun restoreUuid() = unmockkStatic(UUID::class)

    private fun registry() = ProfileRegistry(prefs)

    @Test
    fun `getAllProfiles ignores bookkeeping keys whose value parses as profile JSON`() {
        val manager = registry()
        val metadataJson =
            ProfileManager
                .ProfileMetadata(displayName = ProfileName.fromTrustedSource("Not a profile"))
                .toJson()

        prefs.edit(commit = true) { putString(PREF_WEBVIEW_PROFILE_ID, metadataJson) }

        assertFalse(
            "Bookkeeping keys must never surface as profiles",
            manager.getAllProfiles().containsKey(ProfileId(PREF_WEBVIEW_PROFILE_ID)),
        )
    }

    @Test
    fun `ProfileId DEFAULT must be strictly 'default' to preserve legacy compatibility`() {
        assertEquals("default", ProfileId.DEFAULT.value)
    }

    @Test
    fun `ProfileMetadata JSON round-trip preserves all fields`() {
        val original =
            ProfileManager.ProfileMetadata(
                displayName = ProfileName.fromTrustedSource("Test User"),
                version = 5,
                createdTimestamp = "2025-12-31T23:59:59Z",
            )

        val jsonString = original.toJson()
        val reconstructed = ProfileManager.ProfileMetadata.fromJson(jsonString)

        assertEquals("Serialization round-trip failed!", original, reconstructed)
    }

    @Test
    fun `getAllProfiles returns all registered profiles`() {
        val manager = registry()

        val profile1 = manager.createNewProfile(ProfileName.fromTrustedSource("Work"))
        val profile2 = manager.createNewProfile(ProfileName.fromTrustedSource("Personal"))

        val allProfiles = manager.getAllProfiles()

        assertEquals(3, allProfiles.size)
        assertTrue(allProfiles.containsKey(ProfileId.DEFAULT))
        assertTrue(allProfiles.containsKey(profile1))
        assertTrue(allProfiles.containsKey(profile2))
        assertEquals("Default", allProfiles[ProfileId.DEFAULT]?.displayName?.value)
        assertEquals("Work", allProfiles[profile1]?.displayName?.value)
        assertEquals("Personal", allProfiles[profile2]?.displayName?.value)
    }

    @Test
    fun `getAllProfiles returns only default when no profiles created`() {
        registry()

        val allProfiles = registry().getAllProfiles()

        assertEquals(1, allProfiles.size)
        assertTrue(allProfiles.containsKey(ProfileId.DEFAULT))
    }

    @Test
    fun `a new profile ID that matches an existing profile is generated again`() {
        val manager = registry()
        mockkStatic(UUID::class)
        every { UUID.randomUUID() } returnsMany
            listOf(
                UUID.fromString("aaaaaaaa-0000-4000-8000-000000000000"),
                UUID.fromString("aaaaaaaa-1111-4111-8111-111111111111"),
                UUID.fromString("bbbbbbbb-0000-4000-8000-000000000000"),
            )

        val first = manager.createNewProfile(ProfileName.fromTrustedSource("Work"))
        val second = manager.createNewProfile(ProfileName.fromTrustedSource("Personal"))

        assertEquals(ProfileId("p_aaaaaaaa"), first)
        assertEquals(ProfileId("p_bbbbbbbb"), second)
    }

    @Test
    fun `createNewProfile rejects a name already in use, ignoring case`() {
        val manager = registry()
        manager.createNewProfile(ProfileName.fromTrustedSource("Work"))

        assertThrows(IllegalArgumentException::class.java) {
            manager.createNewProfile(ProfileName.fromTrustedSource("work"))
        }
        assertEquals(2, manager.getAllProfiles().size)
    }

    @Test
    fun `createNewProfile does not log the profile name`() {
        val manager = registry()

        val logs = captureLogs { manager.createNewProfile(ProfileName.fromTrustedSource("Alice")) }

        assertTrue("Expected createNewProfile to log", logs.isNotEmpty())
        assertFalse("Profile name was logged: $logs", logs.any { it.contains("Alice") })
    }

    @Test
    fun `renameProfile updates displayName in registry`() {
        val manager = registry()
        val profileId = manager.createNewProfile(ProfileName.fromTrustedSource("Original Name"))
        val newName = ProfileName.fromTrustedSource("Updated Name")

        manager.renameProfile(profileId, newName)

        val json = prefs.getString(profileId.value, null)
        val metadata = ProfileManager.ProfileMetadata.fromJson(json!!)

        assertEquals(newName, metadata.displayName)
    }

    @Test
    fun `renameProfile preserves version and createdTimestamp`() {
        val manager = registry()
        val profileId = manager.createNewProfile(ProfileName.fromTrustedSource("Original Name"))

        val originalJson = prefs.getString(profileId.value, null)
        val originalMetadata = ProfileManager.ProfileMetadata.fromJson(originalJson!!)

        manager.renameProfile(profileId, ProfileName.fromTrustedSource("New Name"))

        val updatedJson = prefs.getString(profileId.value, null)
        val updatedMetadata = ProfileManager.ProfileMetadata.fromJson(updatedJson!!)

        assertEquals("Version must be preserved", originalMetadata.version, updatedMetadata.version)
        assertEquals(
            "Timestamp must be preserved",
            originalMetadata.createdTimestamp,
            updatedMetadata.createdTimestamp,
        )
    }

    @Test
    fun `renameProfile does not write to disk if name is identical`() {
        val manager = registry()
        val name = ProfileName.fromTrustedSource("No Change")
        val profileId = manager.createNewProfile(name)

        val originalJson = prefs.getString(profileId.value, null)

        manager.renameProfile(profileId, name)

        val currentJson = prefs.getString(profileId.value, null)
        assertEquals("No disk write should occur for identical names", originalJson, currentJson)
    }

    @Test
    fun `renameProfile throws IllegalArgumentException for missing profile`() {
        val manager = registry()
        val fakeId = ProfileId("p_ghost")

        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                manager.renameProfile(fakeId, ProfileName.fromTrustedSource("New Name"))
            }

        assertTrue(exception.message!!.contains("not found"))
    }

    @Test
    fun `renameProfile rejects a name used by another profile, ignoring case`() {
        val manager = registry()
        manager.createNewProfile(ProfileName.fromTrustedSource("Work"))
        val home = manager.createNewProfile(ProfileName.fromTrustedSource("Home"))

        assertThrows(IllegalArgumentException::class.java) {
            manager.renameProfile(home, ProfileName.fromTrustedSource("work"))
        }
        assertEquals("Home", manager.getAllProfiles()[home]?.displayName?.value)
    }

    @Test
    fun `renameProfile can change the case of the profile's own name`() {
        val manager = registry()
        val work = manager.createNewProfile(ProfileName.fromTrustedSource("work"))

        manager.renameProfile(work, ProfileName.fromTrustedSource("Work"))

        assertEquals("Work", manager.getAllProfiles()[work]?.displayName?.value)
    }

    private fun captureLogs(block: () -> Unit): List<String> {
        val messages = mutableListOf<String>()
        val tree =
            object : Timber.Tree() {
                override fun log(
                    priority: Int,
                    tag: String?,
                    message: String,
                    t: Throwable?,
                ) {
                    messages += message
                }
            }
        Timber.plant(tree)
        try {
            block()
        } finally {
            Timber.uproot(tree)
        }
        return messages
    }
}
