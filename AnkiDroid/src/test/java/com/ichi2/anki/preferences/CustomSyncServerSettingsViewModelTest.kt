// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.getEndpoint
import com.ichi2.anki.settings.Prefs
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CustomSyncServerSettingsViewModelTest : RobolectricTest() {
    @Test
    fun `loads existing settings without changing them`() {
        Prefs.customSyncUri = "https://sync.example.com/"
        Prefs.isCustomSyncEnabled = false
        Prefs.customSyncCertificate = "saved certificate"
        Prefs.currentSyncUri = "https://redirect.example.com/"

        val viewModel = CustomSyncServerSettingsViewModel()

        assertEquals(CustomSyncServerState.Disabled, viewModel.server.value)
        assertEquals(CustomSyncServerConfiguration("https://sync.example.com/", "saved certificate"), viewModel.configuration.value)
        assertEquals("https://redirect.example.com/", Prefs.currentSyncUri)
    }

    @Test
    fun `saving a URL enables custom sync and clears the old endpoint`() {
        Prefs.currentSyncUri = "https://redirect.example.com/"
        val viewModel = CustomSyncServerSettingsViewModel()

        viewModel.saveUrl("http://localhost:8080/sync/")

        assertEquals("http://localhost:8080/sync/", Prefs.customSyncUri)
        assertTrue(Prefs.isCustomSyncEnabled)
        assertNull(Prefs.currentSyncUri)
        assertEquals("http://localhost:8080/sync/", getEndpoint())
        assertEquals("http://localhost:8080/sync/".toHttpUrl(), assertIs<CustomSyncServerState.Enabled>(viewModel.server.value).endpoint)
        val restored = CustomSyncServerSettingsViewModel()
        assertEquals(viewModel.server.value, restored.server.value)
        assertEquals(viewModel.configuration.value, restored.configuration.value)
    }

    @Test
    fun `invalid URLs leave saved settings and endpoint unchanged`() {
        Prefs.customSyncUri = "https://sync.example.com/"
        Prefs.isCustomSyncEnabled = true
        Prefs.currentSyncUri = "https://redirect.example.com/"
        val viewModel = CustomSyncServerSettingsViewModel()
        val originalServer = viewModel.server.value
        val originalConfiguration = viewModel.configuration.value

        for (invalid in listOf("not a URL", "https://", "ftp://example.com/", " ")) {
            assertFalse(isValidSyncUrl(invalid), invalid)
            viewModel.saveUrl(invalid)
            assertEquals(originalServer, viewModel.server.value)
            assertEquals(originalConfiguration, viewModel.configuration.value)
            assertEquals(originalConfiguration.url, Prefs.customSyncUri)
            assertTrue(Prefs.isCustomSyncEnabled)
            assertEquals("https://redirect.example.com/", Prefs.currentSyncUri)
        }
    }

    @Test
    fun `clearing the URL disables custom sync and uses AnkiWeb`() {
        Prefs.customSyncUri = "https://sync.example.com/"
        Prefs.isCustomSyncEnabled = true
        Prefs.currentSyncUri = "https://redirect.example.com/"
        val viewModel = CustomSyncServerSettingsViewModel()

        viewModel.saveUrl("")

        assertEquals("", Prefs.customSyncUri)
        assertFalse(Prefs.isCustomSyncEnabled)
        assertEquals(CustomSyncServerState.Disabled, viewModel.server.value)
        assertEquals(CustomSyncServerConfiguration(), viewModel.configuration.value)
        assertNull(getEndpoint())
    }

    @Test
    fun `toggling custom sync preserves the URL and clears cached redirects`() {
        Prefs.customSyncUri = "https://sync.example.com/"
        Prefs.isCustomSyncEnabled = true
        val viewModel = CustomSyncServerSettingsViewModel()

        for (enabled in listOf(false, true)) {
            Prefs.currentSyncUri = "https://redirect.example.com/"
            viewModel.toggleEnabled(enabled)

            assertEquals(enabled, Prefs.isCustomSyncEnabled)
            if (enabled) {
                assertEquals(
                    "https://sync.example.com/".toHttpUrl(),
                    assertIs<CustomSyncServerState.Enabled>(viewModel.server.value).endpoint,
                )
            } else {
                assertEquals(CustomSyncServerState.Disabled, viewModel.server.value)
            }
            assertEquals("https://sync.example.com/", viewModel.configuration.value.url)
            assertEquals("https://sync.example.com/", Prefs.customSyncUri)
            assertNull(Prefs.currentSyncUri)
            assertEquals(if (enabled) Prefs.customSyncUri else null, getEndpoint())
        }
    }

    @Test
    fun `custom sync cannot be enabled without a URL`() {
        val viewModel = CustomSyncServerSettingsViewModel()

        viewModel.toggleEnabled(true)

        assertEquals(CustomSyncServerState.Disabled, viewModel.server.value)
        assertFalse(Prefs.isCustomSyncEnabled)
    }

    @Test
    fun `invalid saved URL cannot produce an enabled server`() {
        Prefs.customSyncUri = "invalid URL"
        val viewModel = CustomSyncServerSettingsViewModel()

        viewModel.toggleEnabled(true)

        assertEquals(CustomSyncServerState.Disabled, viewModel.server.value)
        assertEquals("invalid URL", viewModel.configuration.value.url)
        assertFalse(Prefs.isCustomSyncEnabled)
    }

    @Test
    fun `saving the same enabled URL retains the current redirect`() {
        Prefs.customSyncUri = "https://sync.example.com/"
        Prefs.isCustomSyncEnabled = true
        Prefs.currentSyncUri = "https://redirect.example.com/"

        CustomSyncServerSettingsViewModel().saveUrl("https://sync.example.com/")

        assertEquals("https://redirect.example.com/", Prefs.currentSyncUri)
    }

    @Test
    fun `saving a disabled URL again enables it and clears the redirect`() {
        Prefs.customSyncUri = "https://sync.example.com/"
        Prefs.isCustomSyncEnabled = false
        Prefs.currentSyncUri = "https://redirect.example.com/"

        CustomSyncServerSettingsViewModel().saveUrl("https://sync.example.com/")

        assertTrue(Prefs.isCustomSyncEnabled)
        assertNull(Prefs.currentSyncUri)
    }

    @Test
    fun `rejected certificate does not replace the saved certificate`() {
        Prefs.customSyncCertificate = "saved certificate"
        var attemptedCertificate: String? = null
        val viewModel =
            CustomSyncServerSettingsViewModel(updateCertificate = {
                attemptedCertificate = it
                false
            })

        assertFalse(viewModel.saveCertificate("invalid certificate"))

        assertEquals("invalid certificate", attemptedCertificate)
        assertEquals("saved certificate", Prefs.customSyncCertificate)
        assertEquals("saved certificate", viewModel.configuration.value.certificate)
    }

    @Test
    fun `backend rejects invalid PEM and accepts removing the certificate`() {
        val viewModel = CustomSyncServerSettingsViewModel()

        assertFalse(viewModel.saveCertificate("invalid PEM"))
        assertNull(Prefs.customSyncCertificate)
        assertTrue(viewModel.saveCertificate(""))
        assertEquals("", Prefs.customSyncCertificate)
    }

    @Test
    fun `accepted certificate is applied and persisted including empty input`() {
        val appliedCertificates = mutableListOf<String>()
        val viewModel =
            CustomSyncServerSettingsViewModel(updateCertificate = {
                appliedCertificates += it
                true
            })
        Prefs.currentSyncUri = "https://redirect.example.com/"

        for (certificate in listOf("-----BEGIN CERTIFICATE-----\ncertificate\n-----END CERTIFICATE-----", "")) {
            assertTrue(viewModel.saveCertificate(certificate))
            assertEquals(certificate, Prefs.customSyncCertificate)
            assertEquals(certificate, viewModel.configuration.value.certificate)
            assertEquals(certificate, CustomSyncServerSettingsViewModel().configuration.value.certificate)
            assertEquals("https://redirect.example.com/", Prefs.currentSyncUri)
        }
        assertEquals(listOf("-----BEGIN CERTIFICATE-----\ncertificate\n-----END CERTIFICATE-----", ""), appliedCertificates)
    }
}
