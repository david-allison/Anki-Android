// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.dialogs

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ichi2.compose.ui.preview.AnkiDroidPreview
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AnkiDroidDialogTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `only the exit animation is disabled while the dialog keyboard is visible`() {
        lateinit var dialogView: View
        compose.setContent {
            AnkiDroidPreview {
                AnkiDroidDialog(onDismissRequest = {}) {
                    val view = LocalView.current
                    SideEffect { dialogView = view }
                    Box(Modifier.testTag("dialog_content"))
                }
            }
        }
        compose.waitForIdle()
        val window = requireNotNull(ShadowDialog.getLatestDialog().window)
        // Include a visible IME with no height, as can happen with a hardware keyboard.
        for ((visible, height) in listOf(false to 0, true to 300, true to 0, false to 0)) {
            compose.runOnIdle {
                ViewCompat.dispatchApplyWindowInsets(
                    dialogView,
                    WindowInsetsCompat
                        .Builder()
                        .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, height))
                        .setVisible(WindowInsetsCompat.Type.ime(), visible)
                        .build(),
                )
            }
            compose.waitForIdle()
            val animations =
                window.context.obtainStyledAttributes(
                    window.attributes.windowAnimations,
                    intArrayOf(android.R.attr.windowEnterAnimation, android.R.attr.windowExitAnimation),
                )
            try {
                assertNotEquals(0, animations.getResourceId(0, 0), "Opening should still fade")
                if (visible) {
                    assertEquals(0, animations.getResourceId(1, 0), "Closing must not animate the keyboard snapshot")
                } else {
                    assertNotEquals(0, animations.getResourceId(1, 0), "Closing should fade without the keyboard")
                }
            } finally {
                animations.recycle()
            }
        }
    }

    @Test
    @Config(qualifiers = "w640dp-h320dp-land")
    fun `large dialogs use the View dialog background insets`() {
        compose.setContent {
            AnkiDroidPreview {
                AnkiDroidDialog(onDismissRequest = {}) {
                    Box(Modifier.fillMaxSize().testTag("dialog_content"))
                }
            }
        }
        val content = compose.onNodeWithTag("dialog_content").fetchSemanticsNode().boundsInRoot
        val window = compose.onNode(isDialog()).fetchSemanticsNode().boundsInRoot
        val density =
            RuntimeEnvironment
                .getApplication()
                .resources.displayMetrics.density
        val horizontalMargin = 24 * density
        val verticalMargin = 16 * density
        assertEquals(horizontalMargin, content.left - window.left, 1f, "The dialog must leave 24 dp on the left")
        assertEquals(horizontalMargin, window.right - content.right, 1f, "The dialog must leave 24 dp on the right")
        assertEquals(verticalMargin, content.top - window.top, 1f, "The dialog must leave 16 dp above its content")
        assertEquals(verticalMargin, window.bottom - content.bottom, 1f, "The dialog must leave 16 dp below its content")
    }
}
