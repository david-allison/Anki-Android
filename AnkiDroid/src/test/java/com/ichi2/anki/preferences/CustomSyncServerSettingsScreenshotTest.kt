// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.google.testing.junit.testparameterinjector.TestParameter
import com.ichi2.anki.ComposeScreenshotTest
import com.ichi2.anki.settings.enums.Theme
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test
import org.robolectric.annotation.Config

class CustomSyncServerSettingsScreenshotTest : ComposeScreenshotTest() {
    @Composable
    private fun Screen(
        enabled: Boolean = true,
        certificate: String = "",
        showNavigateUp: Boolean = true,
    ) {
        CustomSyncServerSettingsScreen(
            server =
                if (enabled) {
                    CustomSyncServerState.Enabled("https://sync.example.com/".toHttpUrl())
                } else {
                    CustomSyncServerState.Disabled
                },
            configuration = CustomSyncServerConfiguration(url = "https://sync.example.com/", certificate = certificate),
            onSaveServer = { _, _ -> },
            onSaveCertificate = { true },
            onNavigateUp = {},
            showNavigateUp = showNavigateUp,
        )
    }

    @Test
    fun screen(
        @TestParameter enabled: Boolean,
        @TestParameter(valuesProvider = ThemeProvider::class) theme: Theme,
    ) {
        capture(name = if (enabled) "enabled" else "disabled", theme = theme) { Screen(enabled) }
    }

    @Test
    @Config(qualifiers = "w320dp-h480dp-mdpi")
    fun largeText(
        @TestParameter bottom: Boolean,
    ) {
        capture(
            name = if (bottom) "large_text_bottom" else "large_text_top",
            fontScale = 2f,
            beforeCapture = {
                composeRule.onNodeWithText("Save").assertIsDisplayed()
                if (bottom) {
                    composeRule.onNodeWithText("Sync with your own server", substring = true).performScrollTo().assertIsDisplayed()
                }
            },
        ) { Screen() }
    }

    @Test
    fun invalidUrl() {
        capture(
            name = "invalid_url",
            beforeCapture = {
                composeRule.onNodeWithTag("custom_sync_url").performTextReplacement("not a URL")
                composeRule.onNodeWithText("Enter a valid server URL.").assertIsDisplayed()
                composeRule.onNodeWithText("Save").assertIsNotEnabled()
            },
        ) { Screen() }
    }

    @Test
    @Config(qualifiers = "w480dp-h320dp-land-mdpi")
    fun certificateEditor(
        @TestParameter largeText: Boolean,
    ) {
        capture(
            name = if (largeText) "certificate_editor_large" else "certificate_editor_normal",
            node = isDialog(),
            fontScale = if (largeText) 2f else 1f,
            beforeCapture = {
                composeRule.onNodeWithText("Custom root certificate").performScrollTo().performClick()
                composeRule.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performScrollTo().assertIsDisplayed()
                composeRule.onNode(hasText("Save") and hasAnyAncestor(isDialog())).assertIsDisplayed()
                composeRule.onNodeWithText("Cancel").assertIsDisplayed()
            },
        ) {
            Screen(certificate = "-----BEGIN CERTIFICATE-----\n${"VGhpcyBpcyBhIHRlc3Q=\n".repeat(8)}-----END CERTIFICATE-----")
        }
    }

    @Test
    @Config(qualifiers = "w800dp-h600dp-land-mdpi")
    fun tablet() {
        capture(name = "tablet") { Screen(showNavigateUp = false) }
    }
}
