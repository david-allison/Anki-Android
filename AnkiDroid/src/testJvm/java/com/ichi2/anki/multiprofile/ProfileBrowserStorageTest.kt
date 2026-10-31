// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.multiprofile

import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProfileBrowserStorageTest {
    private val registry = ProfileRegistry(SPMockBuilder().createSharedPreferences())
    private val suffixes = mutableListOf<String>()
    private var removals = 0
    private var completesRemoval = true

    private fun storage(separateDirectories: Boolean) =
        ProfileBrowserStorage(
            registry,
            supportsSeparateDirectories = separateDirectories,
            setDirectorySuffix = { suffixes += it },
            clearCookies = { complete ->
                removals++
                if (completesRemoval) complete()
            },
        )

    private fun addDefault() {
        registry.saveProfile(
            ProfileId.DEFAULT,
            ProfileManager.ProfileMetadata(ProfileName.fromTrustedSource("Default")),
        )
    }

    @Test
    fun `non-default profile uses an isolated directory`() {
        storage(true).configure(ProfileId("p_webview_test"))
        assertEquals(listOf("p_webview_test"), suffixes)
        assertEquals(0, removals)
    }

    @Test
    fun `default profile keeps the default directory`() {
        storage(true).configure(ProfileId.DEFAULT)
        assertEquals(emptyList(), suffixes)
        assertEquals(0, removals)
    }

    @Test
    fun `already initialized WebView does not prevent profile loading`() {
        var attempts = 0
        val storage =
            ProfileBrowserStorage(
                registry,
                supportsSeparateDirectories = true,
                setDirectorySuffix = { suffix ->
                    assertEquals("p_error_test", suffix)
                    attempts++
                    throw IllegalStateException("Already initialized")
                },
                clearCookies = { error("separate storage must not clear cookies") },
            )
        storage.configure(ProfileId("p_error_test"))
        assertEquals(1, attempts)
    }

    @Test
    fun `legacy first run adopts Default without opening the cookie manager`() {
        addDefault()
        storage(false).configure(ProfileId.DEFAULT)
        assertEquals(0, removals)
        assertEquals(ProfileId.DEFAULT, registry.getWebViewProfileId())
    }

    @Test
    fun `legacy same profile does not clear cookies repeatedly`() {
        val storage = storage(false)
        val bob = ProfileId("p_bob")
        storage.configure(bob)
        storage.configure(bob)
        assertEquals(1, removals)
        assertEquals(bob, registry.getWebViewProfileId())
    }

    @Test
    fun `legacy profile change clears cookies before recording ownership`() {
        addDefault()
        val storage = storage(false)
        storage.configure(ProfileId.DEFAULT)
        storage.configure(ProfileId("p_bob"))
        assertEquals(1, removals)
        assertEquals(ProfileId("p_bob"), registry.getWebViewProfileId())
    }

    @Test
    fun `legacy unrecorded owner is cleared when another profile exists`() {
        addDefault()
        registry.createNewProfile(ProfileName.fromTrustedSource("Work"))
        storage(false).configure(ProfileId.DEFAULT)
        assertEquals(1, removals)
        assertEquals(ProfileId.DEFAULT, registry.getWebViewProfileId())
    }

    @Test
    fun `legacy retries until cookie removal completes`() {
        completesRemoval = false
        val storage = storage(false)
        storage.configure(ProfileId("p_bob"))
        storage.configure(ProfileId("p_bob"))
        assertEquals(2, removals)
        assertNull(registry.getWebViewProfileId())
    }
}
