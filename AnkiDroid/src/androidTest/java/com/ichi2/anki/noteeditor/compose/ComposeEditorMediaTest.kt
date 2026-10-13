// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.content.Intent
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.descendants
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.NoteEditorFragment
import com.ichi2.anki.R
import com.ichi2.anki.noteeditor.web.WebEditorView
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.GrantStoragePermission.storagePermission
import com.ichi2.anki.testutil.TestInputMethodRule
import com.ichi2.anki.testutil.awaitJavascript
import com.ichi2.anki.testutil.ensureWebViewIsSupported
import com.ichi2.anki.testutil.evaluate
import com.ichi2.anki.testutil.grantPermissions
import com.ichi2.anki.testutil.notificationPermission
import com.ichi2.anki.testutil.waitUntil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ComposeEditorMediaTest : InstrumentedTest() {
    @get:Rule
    val runtimePermissionRule = grantPermissions(storagePermission, notificationPermission)

    @get:Rule
    val keyboard = TestInputMethodRule()

    @Before
    fun createSyntheticCollection() {
        ensureWebViewIsSupported()
        CollectionManager.setColForTests(emptyCol)
        col.notetypes.setCurrent(col.notetypes.basic)
    }

    @Test
    fun cancellingAttachmentSheetRestoresKeyboardAndCaret() = checkAttachmentCancellation()

    @Test
    fun cancellingAnotherFieldsAttachmentRestoresThePreviouslyFocusedField() = checkAttachmentCancellation(quickAction = true)

    @Test
    fun cancellingAttachmentSheetKeepsAnAlreadyHiddenKeyboardHidden() = checkAttachmentCancellation(keyboardInitiallyVisible = false)

    private fun checkAttachmentCancellation(
        quickAction: Boolean = false,
        keyboardInitiallyVisible: Boolean = true,
    ) {
        withEditor { scenario ->
            val web = scenario.awaitEditor()
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            web.setField(0, "abcdef")
            web.setField(1, "Leave Back unchanged")
            lateinit var model: EditorViewModel
            scenario.onActivity { model = ViewModelProvider(it)[EditorViewModel::class.java] }
            val state = checkNotNull(model.state.value)
            runBlocking {
                withContext(Dispatchers.Main) {
                    model.setFieldToolbar(
                        EditorFieldTarget(state.sessionId, state.generation, 0),
                        EditorFieldToolbar(buttonOrder = listOf(EditorToolbarButton.MEDIA.name)),
                    )
                }
                web.focusField(0)
            }

            fun keyboardVisible(): Boolean {
                var visible = false
                scenario.onActivity {
                    visible = ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
                }
                return visible
            }
            waitUntil(message = { "Keyboard did not open" }) { keyboardVisible() }
            // A middle-of-field caret catches focus restoration that moves it to the end.
            web.evaluate("${richField(0)}.getRootNode().getSelection().collapse(${richField(0)}.firstChild, 3)")
            if (!keyboardInitiallyVisible) {
                device.pressBack()
                waitUntil(message = { "Keyboard did not close" }) { !keyboardVisible() }
            }
            device.waitForIdle()
            if (quickAction) {
                web.evaluate(
                    "document.querySelectorAll('.field-container[data-index=\"1\"] .quick-action')[1].click()",
                )
            } else {
                val attach = By.desc(testContext.getString(R.string.compose_editor_media)).enabled(true)
                checkNotNull(device.wait(Until.findObject(attach), 5_000)).click()
            }
            val sheetItem = By.res(testContext.packageName, "multimedia_action_image")
            assertNotNull(device.wait(Until.findObject(sheetItem), 5_000))
            // Disable automatic window restoration: cancellation must explicitly request the IME.
            scenario.onActivity {
                it.window.setSoftInputMode(
                    (it.window.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE.inv()) or
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN,
                )
                WindowCompat.getInsetsController(it.window, web).hide(WindowInsetsCompat.Type.ime())
            }
            waitUntil(message = { "The attachment sheet did not hide the keyboard" }) { !keyboardVisible() }
            if (quickAction) {
                // An outside tap must behave the same as Back.
                val sheet = checkNotNull(device.findObject(By.res(testContext.packageName, "design_bottom_sheet")))
                device.click(device.displayWidth / 2, sheet.visibleBounds.top / 2)
            } else {
                device.pressBack()
            }
            assertTrue(device.wait(Until.gone(sheetItem), 5_000))
            if (keyboardInitiallyVisible) {
                waitUntil(message = { "Cancelling attachment must restore the previously open keyboard" }) { keyboardVisible() }
                assertEquals(0, runBlocking { web.captureTarget() }?.field)
                // Type through the actual input connection, without restoring a bridge bookmark.
                keyboard.perform("commit", "x")
                web.awaitJavascript("${richField(0)}.textContent.toLowerCase() === 'abcxdef'")
            } else {
                device.waitForIdle()
                assertFalse(keyboardVisible(), "Cancellation must not open a keyboard that was already hidden")
            }
            assertEquals("Leave Back unchanged", runBlocking { web.snapshot().fields[1] })
        }
    }

    private fun withEditor(block: (ActivityScenario<ComposeNoteEditorActivity>) -> Unit) {
        val intent = Intent(testContext, ComposeNoteEditorActivity::class.java).putExtras(NoteEditorFragment.addNoteArgs())
        ActivityScenario.launch<ComposeNoteEditorActivity>(intent).use(block)
    }

    private fun ActivityScenario<ComposeNoteEditorActivity>.awaitEditor(): WebEditorView {
        var editor: WebEditorView? = null
        waitUntil(timeout = 30.seconds, message = { "The production field editor did not appear" }) {
            onActivity { activity ->
                editor = (activity.window.decorView as ViewGroup).descendants.filterIsInstance<WebEditorView>().firstOrNull()
            }
            editor != null
        }
        return checkNotNull(editor).also {
            it.awaitJavascript("${richField(0)}?.isContentEditable === true")
            runBlocking { it.snapshot() }
        }
    }

    private fun WebEditorView.setField(
        index: Int,
        html: String,
    ) {
        evaluate(
            """
            (() => {
                const field = ${richField(index)};
                field.innerHTML = ${JSONObject.quote(html)};
                field.dispatchEvent(new InputEvent('input', { bubbles: true, composed: true, inputType: 'insertText' }));
            })();
            """.trimIndent(),
        )
    }

    private fun richField(index: Int): String =
        "document.querySelector('.field-container[data-index=\"$index\"] .rich-text-editable')?.shadowRoot?.querySelector('anki-editable')"
}
