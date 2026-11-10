// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CommonString
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.settings.Prefs
import com.ichi2.compose.theme.AnkiDroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@RunWith(AndroidJUnit4::class)
class CustomSyncServerSettingsScreenTest : RobolectricTest() {
    @get:Rule
    val composeRule = createComposeRule()

    private val urlTitle get() = targetContext.getString(CommonString.custom_sync_server_base_url_title)
    private val certificateTitle get() = targetContext.getString(CommonString.custom_sync_certificate_title)
    private val cancelText get() = targetContext.getString(CommonString.dialog_cancel)
    private val saveText get() = targetContext.getString(CommonString.save)

    private fun showScreen(viewModel: CustomSyncServerSettingsViewModel = CustomSyncServerSettingsViewModel()) {
        composeRule.setContent {
            AnkiDroidTheme {
                val server by viewModel.server.collectAsState()
                val configuration by viewModel.configuration.collectAsState()
                CustomSyncServerSettingsScreen(
                    server = server,
                    configuration = configuration,
                    onToggleEnabled = viewModel::toggleEnabled,
                    onSaveUrl = viewModel::saveUrl,
                    onSaveCertificate = viewModel::saveCertificate,
                    onNavigateUp = {},
                )
            }
        }
    }

    @Test
    fun `empty switch opens URL editor and cancelling changes nothing`() {
        showScreen()

        composeRule.onNodeWithContentDescription(urlTitle).performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("https://sync.example.com/")
        composeRule.onNodeWithText(cancelText).performClick()

        composeRule.onNodeWithContentDescription(urlTitle).assertIsOff()
        assertEquals(null, Prefs.customSyncUri)
        assertFalse(Prefs.isCustomSyncEnabled)
    }

    @Test
    fun `URL editor rejects invalid input and saves a valid URL`() {
        showScreen()
        composeRule.onNodeWithText(urlTitle).performClick()

        composeRule.onNode(hasSetTextAction()).performTextReplacement("not a URL")
        composeRule.onNodeWithText(saveText).assertIsNotEnabled()

        composeRule.onNode(hasSetTextAction()).performTextReplacement("http://localhost:8080/")
        composeRule.onNodeWithText(saveText).performClick()

        composeRule.onNodeWithContentDescription(urlTitle).assertIsOn()
        composeRule.onNodeWithText("http://localhost:8080/").assertIsDisplayed()
        assertEquals("http://localhost:8080/", Prefs.customSyncUri)
    }

    @Test
    fun `saved URL can be toggled independently of its editor`() {
        Prefs.customSyncUri = "https://sync.example.com/"
        Prefs.isCustomSyncEnabled = true
        showScreen()

        composeRule.onNodeWithContentDescription(urlTitle).performClick().assertIsOff()
        composeRule.onNode(hasSetTextAction()).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(urlTitle).performClick().assertIsOn()
        assertEquals("https://sync.example.com/", Prefs.customSyncUri)
    }

    @Test
    fun `clearing the URL turns the switch off`() {
        Prefs.customSyncUri = "https://sync.example.com/"
        Prefs.isCustomSyncEnabled = true
        showScreen()

        composeRule.onNodeWithText(urlTitle).performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("")
        composeRule.onNodeWithText(saveText).performClick()

        composeRule.onNodeWithContentDescription(urlTitle).assertIsOff()
        assertEquals("", Prefs.customSyncUri)
        assertFalse(Prefs.isCustomSyncEnabled)
    }

    @Test
    fun `invalid certificate shows an error and preserves the saved value`() {
        Prefs.customSyncCertificate = "saved certificate"
        showScreen(CustomSyncServerSettingsViewModel(updateCertificate = { false }))

        composeRule.onNodeWithText(certificateTitle).performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("invalid certificate")
        composeRule.onNodeWithText(saveText).performClick()

        composeRule.onNodeWithText(targetContext.getString(CommonString.dialog_invalid_custom_certificate_title)).assertIsDisplayed()
        assertEquals("saved certificate", Prefs.customSyncCertificate)
    }

    @Test
    fun `multiline certificate is saved and success is shown`() {
        showScreen(CustomSyncServerSettingsViewModel(updateCertificate = { true }))
        val certificate = "-----BEGIN CERTIFICATE-----\ncertificate\n-----END CERTIFICATE-----"

        composeRule.onNodeWithText(certificateTitle).performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement(certificate)
        composeRule.onNodeWithText(saveText).performClick()

        assertEquals(certificate, Prefs.customSyncCertificate)
        composeRule.onNodeWithText(targetContext.getString(CommonString.dialog_updated_custom_certificate)).assertIsDisplayed()
    }

    @Test
    fun `unsaved URL draft survives state restoration`() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            AnkiDroidTheme {
                CustomSyncServerSettingsScreen(
                    server = CustomSyncServerState.Disabled,
                    configuration = CustomSyncServerConfiguration(),
                    onToggleEnabled = {},
                    onSaveUrl = {},
                    onSaveCertificate = { true },
                    onNavigateUp = {},
                )
            }
        }

        composeRule.onNodeWithText(urlTitle).performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("https://draft.example.com/")
        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNode(hasSetTextAction()).assertTextEquals("URL", "https://draft.example.com/")
        assertEquals(null, Prefs.customSyncUri)
    }
}
