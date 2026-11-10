// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.compose.theme.AnkiDroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class PreferenceCardsTest : RobolectricTest(loadBackend = false) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `text and switch have independent actions`() {
        var editCount = 0
        val changes = mutableListOf<Boolean>()
        compose.setContent {
            AnkiDroidTheme {
                PreferenceTextWithSwitchCard(
                    title = "Server",
                    summary = "https://example.com/",
                    isChecked = false,
                    onClick = { editCount++ },
                    onCheckedChange = { changes += it },
                )
            }
        }

        compose.onNodeWithText("Server").performClick()
        assertEquals(1, editCount)
        assertEquals(emptyList(), changes)

        compose.onNodeWithContentDescription("Server").performClick()
        assertEquals(1, editCount)
        assertEquals(listOf(true), changes)
    }

    @Test
    fun `help links open their annotated destination`() {
        var openedUri: String? = null
        val url = "https://example.com/help"
        val handler =
            object : UriHandler {
                override fun openUri(uri: String) {
                    openedUri = uri
                }
            }
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides handler) {
                AnkiDroidTheme {
                    PreferenceHelpSection(
                        buildAnnotatedString {
                            withLink(LinkAnnotation.Url(url)) { append("Learn more") }
                        },
                    )
                }
            }
        }

        compose.onNodeWithText("Learn more").performTouchInput { click() }

        assertEquals(url, openedUri)
    }
}
