// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performFirstLinkClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.settings.Prefs
import com.ichi2.compose.theme.AnkiDroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import androidx.appcompat.R as AppCompatR

@RunWith(AndroidJUnit4::class)
class CustomSyncServerSettingsScreenTest : RobolectricTest() {
    @get:Rule
    val compose = createComposeRule()

    private val toggleTitle get() = targetContext.getString(CommonString.custom_sync_server_enabled_title)
    private val certificateTitle get() = targetContext.getString(CommonString.custom_sync_certificate_title)
    private val saveText get() = targetContext.getString(CommonString.save)
    private val field get() = compose.onNodeWithTag("custom_sync_url")
    private val certificateField get() = compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog()))
    private val dialogSave get() = compose.onNode(hasText(saveText) and hasAnyAncestor(isDialog()))

    @Composable
    private fun Content(
        model: CustomSyncServerSettingsViewModel,
        onBack: () -> Unit = {},
    ) {
        val server by model.server.collectAsState()
        val configuration by model.configuration.collectAsState()
        CustomSyncServerSettingsScreen(
            server = server,
            configuration = configuration,
            onSaveServer = { enabled, url -> model.saveServer(enabled, url) },
            onSaveCertificate = model::saveCertificate,
            onNavigateUp = onBack,
        )
    }

    private fun showScreen(
        model: CustomSyncServerSettingsViewModel = CustomSyncServerSettingsViewModel(),
        onBack: () -> Unit = {},
    ) {
        compose.setContent { AnkiDroidTheme { Content(model, onBack) } }
    }

    @Test
    fun `disabled form retains its URL and has no cancel action`() {
        Prefs.customSyncUri = "https://sync.example.com/"
        showScreen()

        field.assertIsNotEnabled()
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("https://sync.example.com/")))
        compose.onNodeWithContentDescription(toggleTitle).assertIsOff()
        compose.onNodeWithText(saveText).assertIsNotEnabled()
        compose.onNodeWithText(targetContext.getString(CommonString.dialog_cancel)).assertDoesNotExist()
    }

    @Test
    fun `enabling and editing remains pending until toolbar save`() {
        Prefs.currentSyncUri = "https://redirect.example.com/"
        showScreen()
        compose.onNodeWithContentDescription(toggleTitle).performClick()
        field.assertIsEnabled()
        field.performTextReplacement("invalid URL")
        compose.onNodeWithText(saveText).assertIsNotEnabled()
        compose.onNodeWithText(targetContext.getString(CommonString.custom_sync_server_invalid_url)).assertIsDisplayed()

        field.performTextReplacement("http://localhost:8080/")
        field.performImeAction()

        assertFalse(Prefs.isCustomSyncEnabled)
        assertNull(Prefs.customSyncUri)
        assertEquals("https://redirect.example.com/", Prefs.currentSyncUri)
        compose.onNodeWithText(saveText).performClick()
        assertTrue(Prefs.isCustomSyncEnabled)
        assertEquals("http://localhost:8080/", Prefs.customSyncUri)
        assertNull(Prefs.currentSyncUri)
    }

    @Test
    fun `saving disabled mode retains the edited URL`() {
        Prefs.isCustomSyncEnabled = true
        Prefs.customSyncUri = "https://old.example.com/"
        Prefs.currentSyncUri = "https://redirect.example.com/"
        showScreen()
        field.performTextReplacement("https://new.example.com/")
        compose.onNodeWithContentDescription(toggleTitle).performClick()

        field.assertIsNotEnabled()
        assertTrue(Prefs.isCustomSyncEnabled)
        assertEquals("https://old.example.com/", Prefs.customSyncUri)
        compose.onNodeWithText(saveText).performClick()

        assertFalse(Prefs.isCustomSyncEnabled)
        assertEquals("https://new.example.com/", Prefs.customSyncUri)
        assertNull(Prefs.currentSyncUri)
    }

    @Test
    fun `back leaves the pending URL and selection unsaved`() {
        var backCount = 0
        showScreen(onBack = { backCount++ })
        compose.onNodeWithContentDescription(toggleTitle).performClick()
        field.performTextReplacement("https://draft.example.com/")

        compose.onNodeWithContentDescription(targetContext.getString(AppCompatR.string.abc_action_bar_up_description)).performClick()

        assertEquals(1, backCount)
        assertFalse(Prefs.isCustomSyncEnabled)
        assertNull(Prefs.customSyncUri)
    }

    @Test
    fun `draft URL and switch survive state restoration without being saved`() {
        val restoration = StateRestorationTester(compose)
        val model = CustomSyncServerSettingsViewModel()
        restoration.setContent { AnkiDroidTheme { Content(model) } }
        compose.onNodeWithContentDescription(toggleTitle).performClick()
        field.performTextReplacement("https://draft.example.com/")

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithContentDescription(toggleTitle).assertIsOn()
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("https://draft.example.com/")))
        compose.onNodeWithText(saveText).assertIsEnabled()
        assertNull(Prefs.customSyncUri)
        assertFalse(Prefs.isCustomSyncEnabled)
    }

    @Test
    fun `rejected certificate preserves the saved value`() {
        Prefs.customSyncCertificate = "saved certificate"
        showScreen(CustomSyncServerSettingsViewModel(updateCertificate = { false }))
        compose.onNodeWithText(certificateTitle).performScrollTo().performClick()
        certificateField.performTextReplacement("invalid certificate")
        dialogSave.performClick()

        compose.onNodeWithText(targetContext.getString(CommonString.dialog_invalid_custom_certificate_title)).assertIsDisplayed()
        assertEquals("saved certificate", Prefs.customSyncCertificate)
    }

    @Test
    fun `saving a multiline certificate preserves the unsaved server draft`() {
        Prefs.customSyncUri = "https://old.example.com/"
        Prefs.isCustomSyncEnabled = true
        showScreen(CustomSyncServerSettingsViewModel(updateCertificate = { true }))
        field.performTextReplacement("https://draft.example.com/")
        compose.onNodeWithText(certificateTitle).performScrollTo().performClick()
        val certificate = "-----BEGIN CERTIFICATE-----\ncertificate\n-----END CERTIFICATE-----"
        certificateField.performTextReplacement(certificate)
        dialogSave.performClick()

        assertEquals(certificate, Prefs.customSyncCertificate)
        assertEquals("https://old.example.com/", Prefs.customSyncUri)
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("https://draft.example.com/")))
        compose.onNodeWithText(saveText).assertIsEnabled()
        compose.onNodeWithText(targetContext.getString(CommonString.dialog_updated_custom_certificate)).assertIsDisplayed()
    }

    @Test
    fun `combined explanation opens the setup instructions`() {
        val opened = mutableListOf<String>()
        val handler =
            object : UriHandler {
                override fun openUri(uri: String) {
                    opened += uri
                }
            }
        val model = CustomSyncServerSettingsViewModel()
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides handler) {
                AnkiDroidTheme { Content(model) }
            }
        }

        compose.onNodeWithText("Sync with your own server", substring = true).performScrollTo().performFirstLinkClick()

        assertEquals(listOf(targetContext.getString(R.string.link_custom_sync_server_help_learn_more_en)), opened)
    }
}
