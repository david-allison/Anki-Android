// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.testing.junit.testparameterinjector.TestParameter
import com.ichi2.anki.ComposeScreenshotTest
import com.ichi2.anki.settings.enums.Theme
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test
import org.robolectric.annotation.Config

class CustomSyncServerSettingsScreenshotTest : ComposeScreenshotTest() {
    @Test
    fun screen(
        @TestParameter isEnabled: Boolean,
        @TestParameter(valuesProvider = ThemeProvider::class) theme: Theme,
    ) {
        capture(name = if (isEnabled) "enabled" else "disabled", theme = theme) {
            CustomSyncServerSettingsScreen(
                server =
                    if (isEnabled) {
                        CustomSyncServerState.Enabled(
                            "https://sync.example.com/".toHttpUrl(),
                        )
                    } else {
                        CustomSyncServerState.Disabled
                    },
                configuration =
                    CustomSyncServerConfiguration(
                        url = if (isEnabled) "https://sync.example.com/" else "",
                    ),
                onToggleEnabled = {},
                onSaveUrl = {},
                onSaveCertificate = { true },
                onNavigateUp = {},
            )
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h480dp-mdpi")
    fun smallScreenWithLargeText() {
        capture(name = "large_text", fontScale = 2f) {
            CustomSyncServerSettingsScreen(
                server = CustomSyncServerState.Enabled("https://sync.example.com/".toHttpUrl()),
                configuration = CustomSyncServerConfiguration(url = "https://sync.example.com/"),
                onToggleEnabled = {},
                onSaveUrl = {},
                onSaveCertificate = { true },
                onNavigateUp = {},
            )
        }
    }

    @Test
    @Config(qualifiers = "w480dp-h320dp-land-mdpi")
    fun editorInShortWindow(
        @TestParameter certificate: Boolean,
        @TestParameter largeText: Boolean,
    ) {
        capture(
            name = "${if (certificate) "certificate" else "url"}_editor_${if (largeText) "large" else "normal"}",
            node = isDialog(),
            fontScale = if (largeText) 2f else 1f,
            beforeCapture = {
                composeRule
                    .onNodeWithText(if (certificate) "Custom root certificate (PEM)" else "Sync URL")
                    .performScrollTo()
                    .performClick()
                composeRule.onNode(hasSetTextAction()).performScrollTo().assertIsDisplayed()
                composeRule.onNodeWithText("Save").assertIsDisplayed()
                composeRule.onNodeWithText("Cancel").assertIsDisplayed()
            },
        ) {
            CustomSyncServerSettingsScreen(
                server = CustomSyncServerState.Enabled("https://sync.example.com/".toHttpUrl()),
                configuration =
                    CustomSyncServerConfiguration(
                        url = "https://sync.example.com/",
                        certificate = "-----BEGIN CERTIFICATE-----\n${"VGhpcyBpcyBhIHRlc3Q=\n".repeat(8)}-----END CERTIFICATE-----",
                    ),
                onToggleEnabled = {},
                onSaveUrl = {},
                onSaveCertificate = { true },
                onNavigateUp = {},
            )
        }
    }
}
