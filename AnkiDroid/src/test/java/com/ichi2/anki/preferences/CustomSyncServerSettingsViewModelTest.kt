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
    fun `loading disabled settings retains the saved configuration and redirect`() {
        Prefs.customSyncUri = "https://sync.example.com/"
        Prefs.customSyncCertificate = "saved certificate"
        Prefs.currentSyncUri = "https://redirect.example.com/"

        val model = CustomSyncServerSettingsViewModel()

        assertEquals(CustomSyncServerState.Disabled, model.server.value)
        assertEquals(CustomSyncServerConfiguration("https://sync.example.com/", "saved certificate"), model.configuration.value)
        assertEquals("https://redirect.example.com/", Prefs.currentSyncUri)
    }

    @Test
    fun `saving an enabled server updates the endpoint and survives reloading`() {
        Prefs.currentSyncUri = "https://redirect.example.com/"
        val model = CustomSyncServerSettingsViewModel()

        assertTrue(model.saveServer(true, "http://localhost:8080/sync/"))

        assertTrue(Prefs.isCustomSyncEnabled)
        assertEquals("http://localhost:8080/sync/", Prefs.customSyncUri)
        assertNull(Prefs.currentSyncUri)
        assertEquals("http://localhost:8080/sync/", getEndpoint())
        assertEquals("http://localhost:8080/sync/".toHttpUrl(), assertIs<CustomSyncServerState.Enabled>(model.server.value).endpoint)
        val restored = CustomSyncServerSettingsViewModel()
        assertEquals(model.server.value, restored.server.value)
        assertEquals(model.configuration.value, restored.configuration.value)
    }

    @Test
    fun `invalid enabled input does not change any saved settings`() {
        Prefs.customSyncUri = "https://sync.example.com/"
        Prefs.isCustomSyncEnabled = true
        Prefs.currentSyncUri = "https://redirect.example.com/"
        val model = CustomSyncServerSettingsViewModel()
        val originalConfiguration = model.configuration.value
        val originalServer = model.server.value

        for (url in listOf("", " ", "not a URL", "https://", "ftp://example.com/")) {
            assertFalse(model.saveServer(true, url), url)
            assertTrue(Prefs.isCustomSyncEnabled)
            assertEquals("https://sync.example.com/", Prefs.customSyncUri)
            assertEquals("https://redirect.example.com/", Prefs.currentSyncUri)
            assertEquals(originalConfiguration, model.configuration.value)
            assertEquals(originalServer, model.server.value)
        }
    }

    @Test
    fun `disabling and enabling retains the URL and invalidates redirects`() {
        Prefs.customSyncUri = "https://sync.example.com/"
        Prefs.isCustomSyncEnabled = true
        val model = CustomSyncServerSettingsViewModel()

        for (enabled in listOf(false, true)) {
            Prefs.currentSyncUri = "https://redirect.example.com/"
            assertTrue(model.saveServer(enabled, model.configuration.value.url))
            assertEquals(enabled, Prefs.isCustomSyncEnabled)
            assertEquals("https://sync.example.com/", Prefs.customSyncUri)
            assertNull(Prefs.currentSyncUri)
            assertEquals(if (enabled) "https://sync.example.com/" else null, getEndpoint())
        }
    }

    @Test
    fun `saving a URL with the switch off does not enable custom sync`() {
        val model = CustomSyncServerSettingsViewModel()

        assertTrue(model.saveServer(false, "https://sync.example.com/"))

        assertFalse(Prefs.isCustomSyncEnabled)
        assertEquals(CustomSyncServerState.Disabled, model.server.value)
        assertEquals("https://sync.example.com/", Prefs.customSyncUri)
    }

    @Test
    fun `saving an unchanged server retains its redirect`() {
        Prefs.customSyncUri = "https://sync.example.com/"
        Prefs.isCustomSyncEnabled = true
        Prefs.currentSyncUri = "https://redirect.example.com/"

        assertTrue(CustomSyncServerSettingsViewModel().saveServer(true, "https://sync.example.com/"))

        assertEquals("https://redirect.example.com/", Prefs.currentSyncUri)
    }

    @Test
    fun `toggling reads the URL most recently saved by another screen`() {
        Prefs.customSyncUri = "https://old.example.com/"
        val model = CustomSyncServerSettingsViewModel()
        Prefs.customSyncUri = "https://new.example.com/"

        assertTrue(model.setEnabled(true))

        assertEquals("https://new.example.com/", Prefs.customSyncUri)
        assertEquals("https://new.example.com/".toHttpUrl(), assertIs<CustomSyncServerState.Enabled>(model.server.value).endpoint)
    }

    @Test
    fun `a rejected certificate does not replace the saved value`() {
        Prefs.customSyncCertificate = "saved certificate"
        var attempted: String? = null
        val model =
            CustomSyncServerSettingsViewModel(updateCertificate = {
                attempted = it
                false
            })

        assertFalse(model.saveCertificate("invalid certificate"))

        assertEquals("invalid certificate", attempted)
        assertEquals("saved certificate", Prefs.customSyncCertificate)
        assertEquals("saved certificate", model.configuration.value.certificate)
    }

    @Test
    fun `certificates are saved and cleared independently of the custom server`() {
        val applied = mutableListOf<String>()
        val model =
            CustomSyncServerSettingsViewModel(updateCertificate = {
                applied += it
                true
            })
        Prefs.currentSyncUri = "https://redirect.example.com/"
        val certificate = "-----BEGIN CERTIFICATE-----\ncertificate\n-----END CERTIFICATE-----"

        for (value in listOf(certificate, "")) {
            assertTrue(model.saveCertificate(value))
            assertEquals(value, Prefs.customSyncCertificate)
            assertEquals(value, model.configuration.value.certificate)
            assertFalse(Prefs.isCustomSyncEnabled)
            assertEquals("https://redirect.example.com/", Prefs.currentSyncUri)
        }
        assertEquals(listOf(certificate, ""), applied)
    }

    @Test
    fun `backend rejects invalid PEM and accepts removing the certificate`() {
        val model = CustomSyncServerSettingsViewModel()

        assertFalse(model.saveCertificate("invalid PEM"))
        assertNull(Prefs.customSyncCertificate)
        assertTrue(model.saveCertificate(""))
        assertEquals("", Prefs.customSyncCertificate)
    }
}
