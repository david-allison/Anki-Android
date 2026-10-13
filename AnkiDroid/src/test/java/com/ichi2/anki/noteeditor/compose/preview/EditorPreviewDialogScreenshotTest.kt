// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose.preview

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.drawToBitmap
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.ScreenshotTest
import com.ichi2.compose.theme.AnkiDroidTheme
import org.junit.Rule
import org.junit.Test
import kotlin.math.roundToInt
import kotlin.test.assertEquals

class EditorPreviewDialogScreenshotTest : ScreenshotTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun toolbarAndSystemBarInsetsMatchThePreviewBackground() {
        val activity = startActivityNormallyOpenCollectionWithIntent(AnkiActivity::class.java, Intent())
        lateinit var previewView: View
        activity.setContent {
            AnkiDroidTheme {
                EditorPreviewDialog(onDismiss = {}) { modifier ->
                    // Inflate the real preview layout so the assertion compares Compose with the native surface.
                    AndroidView(
                        modifier = modifier,
                        factory = { context ->
                            LayoutInflater.from(context).inflate(R.layout.fragment_template_previewer, null).also {
                                // WebView lifecycle belongs to the Fragment; this test renders its surrounding native layout.
                                it.findViewById<ViewGroup>(R.id.web_view_container).removeAllViews()
                                previewView = it
                            }
                        },
                    )
                }
            }
        }
        composeRule.waitForIdle()
        captureScreen("matching_preview_background")
        val image = previewView.rootView.drawToBitmap()
        val background = (previewView.background as ColorDrawable).color
        val title = composeRule.onNodeWithText(getResourceString(CommonString.card_editor_preview_card)).fetchSemanticsNode().boundsInWindow
        assertEquals(background, image.getPixel(0, title.center.y.roundToInt()), "The toolbar must match the preview frame")
        assertEquals(background, image.getPixel(0, 0), "The status bar inset must match the preview frame")
        assertEquals(background, image.getPixel(0, image.height - 1), "The navigation bar inset must match the preview frame")
    }
}
