// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CommonString
import com.ichi2.anki.NoteEditorFragment
import com.ichi2.anki.NoteEditorFragment.Companion.NoteEditorCaller
import com.ichi2.anki.R
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.noteeditor.web.WebEditorAction
import com.ichi2.anki.noteeditor.web.WebEditorSettings
import com.ichi2.anki.noteeditor.web.WebEditorTarget
import com.ichi2.anki.noteeditor.web.WebEditorView
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.GrantStoragePermission.storagePermission
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
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Drives the production Compose activity, WebView bridge, collection Save and embedded preview. */
class ComposeNoteEditorTest : InstrumentedTest() {
    @get:Rule
    val runtimePermissionRule = grantPermissions(storagePermission, notificationPermission)

    @Before
    fun createSyntheticCollection() {
        ensureWebViewIsSupported()
        CollectionManager.setColForTests(emptyCol)
        col.notetypes.setCurrent(col.notetypes.basic)
    }

    @Test
    fun tagRemovalSnackbarCanBeDismissed() {
        withTagRemovalSnackbar { scenario, device, message ->
            waitUntil(message = { "Snackbar entrance animation did not finish" }) {
                var settled = false
                scenario.onActivity { activity ->
                    val text = activity.findViewById<View>(com.google.android.material.R.id.snackbar_text)
                    val bar = text?.parent?.parent as? View
                    settled = bar != null && bar.height > 0 && bar.translationY == 0f && bar.alpha == 1f
                }
                settled
            }
            val feedback = checkNotNull(device.findObject(By.text(message))).visibleBounds
            device.swipe(feedback.left + 8, feedback.centerY(), device.displayWidth - 8, feedback.centerY(), 20)
            assertTrue(device.wait(Until.gone(By.text(message)), 2_000), "Swiping must dismiss tag feedback")
            assertEquals(listOf("Vocabulary"), scenario.editorState().tags)
        }
    }

    @Test
    fun tagRemovalSnackbarExpires() {
        withTagRemovalSnackbar { scenario, device, message ->
            assertTrue(device.wait(Until.gone(By.text(message)), 8_000), "Tag removal feedback must expire without interaction")
            assertEquals(listOf("Vocabulary"), scenario.editorState().tags)
        }
    }

    private fun withTagRemovalSnackbar(block: (ActivityScenario<ComposeNoteEditorActivity>, UiDevice, String) -> Unit) {
        withEditor { scenario ->
            val web = scenario.awaitEditor()
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            scenario.onActivity { ViewModelProvider(it)[EditorViewModel::class.java].setTags(listOf("Japanese", "Vocabulary")) }
            runBlocking { web.focusField(0) }

            fun keyboardVisible(): Boolean {
                var visible = false
                scenario.onActivity {
                    visible = ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
                }
                return visible
            }
            waitUntil(message = { "Keyboard did not open" }) { keyboardVisible() }
            device.pressBack()
            waitUntil(message = { "Keyboard did not close" }) { !keyboardVisible() }
            val remove = By.desc(testContext.getString(R.string.compose_editor_remove_tag, "Japanese"))
            checkNotNull(device.wait(Until.findObject(remove), 5_000)).click()
            val message = testContext.getString(R.string.compose_editor_tag_removed, "Japanese")
            assertTrue(device.wait(Until.hasObject(By.text(message)), 2_000))
            block(scenario, device, message)
        }
    }

    @Test
    fun tagsHideWhileTypingAndSupportRemovalUndoAndTheExistingDialog() {
        withEditor { scenario ->
            val web = scenario.awaitEditor()
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            web.setField(0, "Unsaved question")
            scenario.onActivity { ViewModelProvider(it)[EditorViewModel::class.java].setTags(listOf("Japanese", "Vocabulary")) }
            runBlocking { web.focusField(0) }

            fun keyboardVisible(): Boolean {
                var visible = false
                scenario.onActivity {
                    visible = ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
                }
                return visible
            }
            waitUntil(message = { "Keyboard did not open" }) { keyboardVisible() }
            val remove = By.desc(testContext.getString(R.string.compose_editor_remove_tag, "Japanese"))
            assertFalse(device.hasObject(remove), "Tags must not consume space while typing on a phone")
            captureScreenshot("tags-hidden-while-typing", web)
            device.pressBack()
            waitUntil(message = { "Keyboard did not close" }) { !keyboardVisible() }
            val selection = runBlocking { web.captureTarget() }
            captureScreenshot("tags-bottom", web)
            checkNotNull(device.wait(Until.findObject(remove), 5_000)).click()
            waitUntil(message = { "Inline removal did not update the draft" }) { scenario.editorState().tags == listOf("Vocabulary") }
            assertEquals(selection, runBlocking { web.captureTarget() })
            assertFalse(keyboardVisible())
            checkNotNull(device.wait(Until.findObject(By.text(testContext.getString(CommonString.undo))), 5_000)).click()
            waitUntil(message = { "Undo did not restore the tag" }) { scenario.editorState().tags == listOf("Japanese", "Vocabulary") }
            checkNotNull(device.wait(Until.findObject(By.text("Japanese")), 5_000)).click()
            assertTrue(device.wait(Until.hasObject(By.res(testContext.packageName, "tags_list")), 5_000))
            checkNotNull(device.wait(Until.findObject(By.res("android", "button2")), 5_000)).click()
            assertTrue(device.wait(Until.gone(By.res(testContext.packageName, "tags_list")), 5_000))
            assertEquals(listOf("Unsaved question", ""), runBlocking { web.snapshot().fields })
            scenario.recreate()
            val restored = scenario.awaitEditor()
            assertEquals(listOf("Japanese", "Vocabulary"), scenario.editorState().tags)
            assertEquals(listOf("Unsaved question", ""), runBlocking { restored.snapshot().fields })
        }
    }

    @Test
    fun globalOptionsPersistAcrossRecreationWithoutChangingTheDraft() {
        withEditor { scenario ->
            val web = scenario.awaitEditor()
            web.setField(0, "Unsaved <b>question</b>")
            val before = runBlocking { web.snapshot().fields }
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            for (label in listOf(
                R.string.compose_editor_shrink_images,
                R.string.compose_editor_mathjax_preview,
                R.string.compose_editor_auto_close_tags,
            )) {
                checkNotNull(
                    device.wait(Until.findObject(By.desc(testContext.getString(R.string.compose_editor_options)).enabled(true)), 5_000),
                ).click()
                checkNotNull(device.wait(Until.findObject(By.text(testContext.getString(label))), 5_000)).click()
                waitUntil(message = { "The option did not finish applying" }) {
                    device.hasObject(By.desc(testContext.getString(R.string.compose_editor_options)).enabled(true))
                }
            }
            waitUntil(message = { "Global editor options did not persist" }) {
                scenario.editorState().settings ==
                    WebEditorSettings(false, false, false)
            }
            assertEquals(before, runBlocking { web.snapshot().fields })
            assertFalse(scenario.editorState().hasMetadataChanges)
            scenario.recreate()
            val restored = scenario.awaitEditor()
            assertEquals(before, runBlocking { restored.snapshot().fields })
            assertEquals(
                WebEditorSettings(false, false, false),
                scenario.editorState().settings,
            )
            assertEquals(0, runBlocking { withCol { noteCount() } })
        }
    }

    @Test
    fun boldCanBeTurnedOffBeforeTypingInAnEmptyField() {
        withEditor { scenario ->
            val web = scenario.awaitEditor()
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            runBlocking { web.focusField(1) }

            fun keyboardVisible(): Boolean {
                var visible = false
                scenario.onActivity {
                    visible = ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
                }
                return visible
            }
            waitUntil(message = { "Keyboard did not open" }) { keyboardVisible() }
            device.pressBack()
            waitUntil(message = { "Keyboard did not close" }) { !keyboardVisible() }

            val bold = By.desc(testContext.getString(R.string.compose_editor_bold)).enabled(true)
            for (enabled in listOf(true, false, true, false)) {
                checkNotNull(device.wait(Until.findObject(bold), 5_000)).click()
                waitUntil(message = { "Bold did not become checked=$enabled in an empty field" }) {
                    val icon = device.findObject(bold) ?: return@waitUntil false
                    (if (icon.isCheckable) icon else icon.parent)?.isChecked == enabled
                }
            }
            val target = assertNotNull(runBlocking { web.captureTarget() })
            assertTrue(runBlocking { web.execute(WebEditorAction.INSERT_TEXT, target, "plain text") })
            assertEquals(listOf("", "plain text"), runBlocking { web.snapshot().fields })
        }
    }

    @Test
    fun formattingIndicatorsFollowSelectionCommandsAndSourceMode() {
        withEditor { scenario ->
            val web = scenario.awaitEditor()
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            web.setField(0, "<b><i>formatted</i></b> plain")
            runBlocking { web.focusField(0) }
            web.evaluate(
                """
                (() => {
                    const field = ${richField(0)};
                    const text = field.querySelector('i').firstChild;
                    field.getRootNode().getSelection().setBaseAndExtent(text, 0, text, text.length);
                })()
                """.trimIndent(),
            )

            fun awaitChecked(
                label: Int,
                checked: Boolean,
            ) {
                val description = testContext.getString(label)
                waitUntil(message = { "$description did not become checked=$checked" }) {
                    val icon = device.findObject(By.desc(description)) ?: return@waitUntil false
                    (if (icon.isCheckable) icon else icon.parent)?.isChecked == checked
                }
            }
            awaitChecked(R.string.compose_editor_bold, true)
            awaitChecked(R.string.compose_editor_italic, true)
            awaitChecked(R.string.compose_editor_underline, false)
            captureScreenshot("selected-formatting", web)
            device.findObject(By.desc(testContext.getString(R.string.compose_editor_bold))).click()
            awaitChecked(R.string.compose_editor_bold, false)
            awaitChecked(R.string.compose_editor_italic, true)
            web.awaitJavascript("!${richField(0)}.querySelector('b,strong')")

            runBlocking { web.focusField(1) }
            awaitChecked(R.string.compose_editor_italic, false)
            device.findObject(By.desc(testContext.getString(R.string.compose_editor_source))).click()
            awaitChecked(R.string.compose_editor_source, true)
            awaitChecked(R.string.compose_editor_bold, false)
            web.awaitJavascript("document.querySelector('.CodeMirror')?.CodeMirror.hasFocus() === true")
            assertEquals(0, runBlocking { withCol { noteCount() } })
        }
    }

    @Test
    fun quickActionsPinAndOpenGalleryWithoutSelectingTheDestination() {
        withEditor { scenario ->
            var web = scenario.awaitEditor()
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val current = scenario.editorState()
            val model = scenario.editorModel()
            runBlocking {
                withContext(Dispatchers.Main) {
                    model.setFieldToolbar(
                        EditorFieldTarget(current.sessionId, current.generation, 1),
                        current.fields[1].toolbar.copy(quickActions = emptyList()),
                    )
                }
                web.focusField(1)
            }
            checkNotNull(
                device.wait(Until.findObject(By.desc(testContext.getString(R.string.compose_editor_field_options))), 5_000),
            ).click()
            clickText(testContext.getString(R.string.compose_editor_quick_actions))
            device.findObjects(By.text(testContext.getString(R.string.compose_editor_pin_field))).last().click()
            clickText(testContext.getString(R.string.multimedia_editor_image_field_editing_galery))
            device.findObjects(By.text(testContext.getString(CommonString.save))).last().click()
            waitUntil(message = { "Quick actions did not save for Back" }) {
                scenario
                    .editorState()
                    .fields[1]
                    .toolbar.quickActions == listOf("pin", "gallery")
            }
            device.findObject(By.desc(testContext.getString(R.string.compose_editor_field_options))).click()
            web.setField(0, "Keep the Front caret here")
            web.setField(1, "Insert media in Back")
            runBlocking { web.focusField(0) }
            val before = runBlocking { web.snapshot().fields }
            val selection = runBlocking { web.captureTarget() }
            val wasPinned = scenario.editorState().fields[1].sticky
            val pinLabel = testContext.getString(if (wasPinned) R.string.compose_editor_unpin_field else R.string.compose_editor_pin_field)
            captureScreenshot("quick-actions-before-pin", web)
            checkNotNull(device.wait(Until.findObject(By.text("$pinLabel: Back")), 5_000)).click()
            waitUntil(message = { "Quick pin did not update Back" }) {
                scenario.editorState().fields[1].sticky != wasPinned
            }
            assertEquals(selection, runBlocking { web.captureTarget() })
            assertEquals(before, runBlocking { web.snapshot().fields })
            captureScreenshot("quick-actions-back-pinned-front-focused", web)

            val mediaTarget = AtomicReference<WebEditorTarget>()
            scenario.onActivity {
                val nativeHandler = web.onQuickAction
                web.onQuickAction = { action, target ->
                    if (action == "gallery") mediaTarget.set(target)
                    nativeHandler?.invoke(action, target)
                }
            }
            val gallery = testContext.getString(R.string.multimedia_editor_image_field_editing_galery)
            checkNotNull(device.wait(Until.findObject(By.text("$gallery: Back")), 5_000)).click()
            waitUntil(message = { "Gallery did not open directly" }) {
                mediaTarget.get() != null && device.currentPackageName != testContext.packageName
            }
            assertEquals(1, mediaTarget.get().field)
            device.pressBack()
            waitUntil(message = { "Cancelling Gallery did not return to the editor" }) {
                scenario.state == Lifecycle.State.RESUMED && device.currentPackageName == testContext.packageName
            }
            assertEquals(before, runBlocking { web.snapshot().fields })
            assertEquals(selection, runBlocking { web.captureTarget() })
            scenario.recreate()
            web = scenario.awaitEditor()
            assertEquals(
                listOf("pin", "gallery"),
                scenario
                    .editorState()
                    .fields[1]
                    .toolbar.quickActions,
            )
            assertEquals(before, runBlocking { web.snapshot().fields })
        }
    }

    @Test
    fun fieldToolbarFollowsFocusAndSurvivesRecreation() {
        // Global templates outlive the synthetic collection; start this test with a known library.
        Prefs.sharedPrefs
            .edit()
            .apply {
                Prefs.sharedPrefs.all.keys
                    .filter { it.startsWith("composeEditorFieldToolbar:") }
                    .forEach { remove(it) }
                remove("composeEditorCustomButtonTemplates")
                remove("note_editor_custom_buttons")
            }.commit()
        withEditor { scenario ->
            var web = scenario.awaitEditor()
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val bold = By.desc(testContext.getString(R.string.compose_editor_bold))
            runBlocking { web.focusField(0) }
            checkNotNull(
                device.wait(Until.findObject(By.desc(testContext.getString(R.string.compose_editor_field_options))), 5_000),
            ).click()
            checkNotNull(
                device.wait(Until.findObject(By.text(testContext.getString(R.string.compose_editor_custom_buttons))), 5_000),
            ).click()
            checkNotNull(
                device.wait(Until.findObject(By.text(testContext.getString(com.ichi2.anki.CommonString.add_toolbar_item))), 5_000),
            ).click()
            assertTrue(device.wait(Until.hasObject(By.text(testContext.getString(R.string.compose_editor_custom_button))), 5_000))
            val inputs = device.findObjects(By.clazz("android.widget.EditText"))
            assertEquals(3, inputs.size)
            inputs[0].text = "Wrap"
            inputs[1].text = "<b>"
            inputs[2].text = "</b>"
            val save = By.text(testContext.getString(com.ichi2.anki.CommonString.save))
            device.findObjects(save).last().click()
            assertTrue(device.wait(Until.hasObject(By.text("Wrap")), 5_000))
            device.findObjects(save).last().click()
            waitUntil(message = { "The global template did not save" }) {
                scenario.editorState().customButtonTemplates.any { it.label == "Wrap" }
            }
            assertTrue(scenario.editorState().fields.all { it.toolbar.customButtons.isEmpty() })
            checkNotNull(device.wait(Until.findObject(By.text(testContext.getString(R.string.compose_editor_toolbar))), 5_000)).click()
            checkNotNull(device.wait(Until.findObject(By.text(testContext.getString(R.string.compose_editor_bold))), 5_000)).click()
            device.findObject(By.text("Wrap")).click()
            val handle =
                checkNotNull(
                    device.findObject(By.desc(testContext.getString(R.string.compose_editor_reorder_button, "Wrap"))),
                ).visibleCenter
            val destination = checkNotNull(device.findObject(By.text(testContext.getString(R.string.compose_editor_italic)))).visibleBounds
            assertTrue(device.swipe(handle.x, handle.y, handle.x, destination.bottom, 30))
            device.findObjects(save).last().click()
            waitUntil(message = { "The field toolbar did not save" }) {
                scenario
                    .editorState()
                    .fields[0]
                    .toolbar.customButtons
                    .any { it.label == "Wrap" }
            }
            device.findObject(By.desc(testContext.getString(R.string.compose_editor_field_options))).click()
            assertTrue(device.wait(Until.hasObject(By.text("Wrap")), 5_000))
            assertFalse(device.hasObject(bold))
            assertTrue(
                device.findObject(By.desc(testContext.getString(R.string.compose_editor_italic))).visibleBounds.right <=
                    device.findObject(By.text("Wrap")).visibleBounds.left,
            )
            web.setField(0, "alpha")
            web.evaluate(
                """
                (() => {
                    const field = ${richField(0)};
                    field.focus();
                    const range = document.createRange();
                    range.selectNodeContents(field);
                    const selection = field.getRootNode().getSelection();
                    selection.removeAllRanges(); selection.addRange(range);
                })()
                """.trimIndent(),
            )
            device.findObject(By.text("Wrap")).click()
            web.awaitJavascript("${richField(0)}.querySelector('b,strong')?.textContent === 'alpha'")
            runBlocking { web.focusField(1) }
            assertTrue(device.wait(Until.hasObject(bold), 5_000))
            assertFalse(device.hasObject(By.text("Wrap")))
            val configuredToolbar = scenario.editorState().fields[0].toolbar
            scenario.recreate()
            web = scenario.awaitEditor()
            assertEquals(configuredToolbar, scenario.editorState().fields[0].toolbar)
            // Mounted fields precede completion of draft restoration. Wait for native controls before focusing.
            assertTrue(device.wait(Until.hasObject(save.enabled(true)), 5_000))
            runBlocking { web.focusField(0) }
            assertTrue(device.wait(Until.hasObject(By.text("Wrap")), 5_000))
            assertFalse(device.hasObject(bold))
            assertTrue(
                device.findObject(By.desc(testContext.getString(R.string.compose_editor_italic))).visibleBounds.right <=
                    device.findObject(By.text("Wrap")).visibleBounds.left,
            )
            assertEquals(0, runBlocking { withCol { noteCount() } })
        }
    }

    @Test
    fun selectiveEraserKeepsItsChoicesAndCapturedSelection() {
        Prefs.sharedPrefs
            .edit()
            .remove("composeEditorRemovedFormats")
            .commit()
        withEditor { scenario ->
            var web = scenario.awaitEditor()
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            web.setField(0, "<b><i>alpha</i></b>")
            web.setField(1, "beta")

            fun selectFront() {
                runBlocking { web.focusField(0) }
                web.evaluate(
                    """
                    (() => {
                        const field = ${richField(0)};
                        const text = field.querySelector('i').firstChild;
                        field.getRootNode().getSelection().setBaseAndExtent(text, 0, text, text.length);
                    })()
                    """.trimIndent(),
                )
            }

            fun openEraser() {
                val eraser = By.desc(testContext.getString(R.string.compose_editor_remove_format)).enabled(true)
                repeat(6) {
                    device.findObject(eraser)?.let { button ->
                        button.click()
                        return
                    }
                    val end =
                        checkNotNull(
                            device.findObject(By.desc(testContext.getString(R.string.compose_editor_field_options))),
                        ).visibleBounds
                    val start = end.left - end.width() * 2
                    device.swipe(start, end.centerY(), start / 3, end.centerY(), 30)
                    device.waitForIdle()
                }
                error("Eraser did not scroll into view")
            }
            selectFront()
            openEraser()
            captureScreenshot("format-remove-choices", web)
            // A cancelled dialog must leave the draft untouched.
            clickText(testContext.getString(CommonString.dialog_cancel))
            assertEquals(listOf("<b><i>alpha</i></b>", "beta"), runBlocking { web.snapshot().fields })
            selectFront()
            openEraser()
            for ((action, label) in editorRemovableFormats) {
                if (action == WebEditorAction.BOLD) continue
                val text = testContext.getString(label)
                // Capturing the web selection opens the dialog asynchronously.
                val choice = checkNotNull(device.wait(Until.findObject(By.text(text)), 5_000)).parent
                assertTrue(choice.isCheckable, "$text must expose its checked state")
                assertTrue(choice.isChecked)
                choice.click()
                waitUntil(message = { "$text did not become unchecked" }) {
                    device.findObject(By.text(text))?.parent?.isChecked == false
                }
            }
            scenario.recreate()
            web = scenario.awaitEditor()
            captureScreenshot("format-remove-restored", web)
            runBlocking { web.focusField(1) }
            clickText(testContext.getString(CommonString.dialog_ok))
            captureScreenshot("format-remove-applied", web)
            web.awaitJavascript("!${richField(0)}.querySelector('b,strong') && ${richField(0)}.querySelector('i')?.textContent === 'alpha'")
            assertEquals("beta", runBlocking { web.snapshot().fields[1] })
            assertEquals("[\"BOLD\"]", Prefs.sharedPrefs.getString("composeEditorRemovedFormats", null))
            assertTrue(runBlocking { web.execute(WebEditorAction.UNDO, web.captureTarget()) })
            assertEquals(listOf("<b><i>alpha</i></b>", "beta"), runBlocking { web.snapshot().fields })
            assertEquals(0, runBlocking { withCol { noteCount() } })
        }
    }

    @Test
    fun nativeColourAndEquationDialogsKeepTheirCapturedDestination() {
        withEditor { scenario ->
            val web = scenario.awaitEditor()
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            web.setField(0, "alpha")
            web.setField(1, "beta")

            fun selectFront() {
                runBlocking { web.focusField(0) }
                web.evaluate(
                    """
                    (() => {
                        const field = ${richField(0)};
                        const range = new Range();
                        range.selectNodeContents(field);
                        const selection = field.getRootNode().getSelection();
                        selection.removeAllRanges();
                        selection.addRange(range);
                    })()
                    """.trimIndent(),
                )
            }

            fun toolbar(label: Int) {
                val target = By.desc(testContext.getString(label)).enabled(true)
                repeat(5) {
                    device.findObject(target)?.let { button ->
                        button.click()
                        return
                    }
                    val end =
                        checkNotNull(
                            device.findObject(By.desc(testContext.getString(R.string.compose_editor_field_options)).enabled(true)),
                        ).visibleBounds
                    // The icon bounds are narrower than the fixed button's touch target.
                    val start = end.left - end.width() * 2
                    device.swipe(start, end.centerY(), start / 3, end.centerY(), 30)
                    device.waitForIdle()
                }
                error("Toolbar item not found: ${testContext.getString(label)}")
            }
            selectFront()
            toolbar(R.string.compose_editor_text_color)
            checkNotNull(device.wait(Until.findObject(By.desc("#1976d2")), 5_000)).click()
            captureScreenshot("format-colour", web)
            runBlocking { web.focusField(1) }
            clickText(testContext.getString(CommonString.dialog_ok))
            web.awaitJavascript("getComputedStyle(${richField(0)}.querySelector('font,span')).color === 'rgb(25, 118, 210)'")
            assertEquals("beta", runBlocking { web.snapshot().fields[1] })

            web.setField(0, "alpha")
            selectFront()
            toolbar(R.string.compose_editor_equations)
            val formula = checkNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5_000))
            formula.text = "x^2"
            captureScreenshot("format-equation", web)
            clickText(testContext.getString(CommonString.dialog_ok))
            web.awaitJavascript("${richField(0)}.querySelector('anki-mathjax')?.dataset.mathjax === 'x^2'")
            assertEquals(listOf("\\(x^2\\)", "beta"), runBlocking { web.snapshot().fields })
            // A tap on the rendered formula reopens the same native editor.
            web.evaluate("${richField(0)}.querySelector('anki-mathjax').click()")
            checkNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5_000)).text = "y^3"
            clickText(testContext.getString(CommonString.dialog_ok))
            web.awaitJavascript("${richField(0)}.querySelector('anki-mathjax')?.dataset.mathjax === 'y^3'")
            assertEquals(listOf("\\(y^3\\)", "beta"), runBlocking { web.snapshot().fields })
        }
    }

    @Test
    fun fieldAndTemplateManagementPreserveTheUnsavedDraft() {
        withEditor { scenario ->
            val web = scenario.awaitEditor()
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            web.setField(0, "<b>Unsaved question</b>")
            web.setField(1, "Unsaved answer")
            val before = runBlocking { web.snapshot().fields }
            val generation = scenario.editorState().generation

            fun openManagement(label: Int) {
                checkNotNull(
                    device.wait(Until.findObject(By.desc(testContext.getString(R.string.compose_editor_options)).enabled(true)), 5_000),
                ).click()
                captureScreenshot("management-options", web)
                clickText(testContext.getString(label))
            }
            openManagement(R.string.compose_editor_fields)
            clickText("Back")
            clickText(testContext.getString(CommonString.model_field_editor_rename))
            checkNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5_000)).text = "Answer"
            clickText(testContext.getString(CommonString.rename))
            checkNotNull(device.wait(Until.findObject(By.text("Answer")), 5_000))
            device.pressBack()
            checkNotNull(device.wait(Until.findObject(By.desc(testContext.getString(R.string.compose_editor_options))), 10_000))
            waitUntil(message = { "The renamed field did not reload" }) {
                scenario.editorState().fields[1].name == "Answer"
            }
            val returned = scenario.awaitEditor()
            assertEquals(before, runBlocking { returned.snapshot().fields })
            assertTrue(runBlocking { returned.snapshot().hasChanges })
            assertEquals(generation + 1, scenario.editorState().generation)

            openManagement(R.string.compose_editor_cards)
            checkNotNull(device.wait(Until.findObject(By.res(testContext.packageName, "sliding_tabs")), 10_000))
            device.pressBack()
            checkNotNull(device.wait(Until.findObject(By.desc(testContext.getString(R.string.compose_editor_options))), 10_000))
            val afterCards = scenario.awaitEditor()
            assertEquals(before, runBlocking { afterCards.snapshot().fields })
            assertEquals(generation + 1, scenario.editorState().generation)
            scenario.recreate()
            assertEquals(before, runBlocking { scenario.awaitEditor().snapshot().fields })
            assertEquals(0, runBlocking { withCol { noteCount() } })
        }
    }

    @Test
    fun largeNotesKeepUnmountedFieldsThroughRecreationAndSave() {
        val type = col.notetypes.basic.deepClone()
        while (type.fields.size < 400) {
            type.jsonObject.getJSONArray("flds").put(col.notetypes.newField("Field ${type.fields.size + 1}").jsonObject)
        }
        col.notetypes.save(type)
        val note = col.newNote(type)
        val expected = MutableList(400) { "<B data-field='$it'>Original&nbsp;$it</B>" }
        expected.forEachIndexed { index, html -> note.fields[index] = html }
        col.addNote(note, col.decks.id("Default"))
        val arguments =
            Bundle().apply {
                putInt(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.EDIT.value)
                putLong(NoteEditorFragment.EXTRA_CARD_ID, note.firstCard(col).id)
            }
        withEditor(arguments) { scenario ->
            val web = scenario.awaitEditor()
            // The viewport may be a phone or a tablet; distant inputs stay deferred.
            web.awaitJavascript("${richField(199)} === undefined && ${richField(399)} === undefined")
            assertEquals(expected, runBlocking { web.snapshot().fields })
            assertFalse(runBlocking { web.snapshot().hasChanges })
            for (index in listOf(0, 199, 399)) {
                runBlocking { web.focusField(index) }
                expected[index] = "<b>Unsaved $index 日本</b>"
                web.setField(index, expected[index])
            }
            // Visiting an untouched distant field must not persist DOM normalization.
            runBlocking { web.focusField(299) }
            assertEquals(expected, runBlocking { web.snapshot().fields })
            val model = scenario.editorModel()
            runBlocking { web.updateHostState(withContext(Dispatchers.Main) { model.hostState() }) }

            scenario.onActivity { activity ->
                val state = Bundle()
                InstrumentationRegistry.getInstrumentation().callActivityOnSaveInstanceState(activity, state)
                val parcel = android.os.Parcel.obtain()
                try {
                    parcel.writeBundle(state)
                    assertTrue(parcel.dataSize() < 16_384, "Field contents must stay out of Android saved state")
                } finally {
                    parcel.recycle()
                }
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertEquals(expected, runBlocking { web.snapshot().fields })
            scenario.recreate()
            val restored = scenario.awaitEditor()
            assertEquals(expected, runBlocking { restored.snapshot().fields })
            assertTrue(runBlocking { restored.snapshot().hasChanges })
            restored.awaitJavascript("${richField(199)} === undefined && ${richField(399)} === undefined")
            clickText(testContext.getString(CommonString.save))
            waitUntil(timeout = 30.seconds, message = { "Large note Save did not close the editor" }) {
                scenario.state == Lifecycle.State.DESTROYED
            }
            assertEquals(expected, runBlocking { withCol { getNote(note.id).fields.toList() } })
        }
    }

    @Test
    fun tappingCurrentOrDifferentFieldClosesOptions() {
        withEditor { scenario ->
            val web = scenario.awaitEditor()
            runBlocking { web.focusField(1) }
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val options = By.desc(testContext.getString(R.string.compose_editor_field_options)).enabled(true)
            val bold = By.desc(testContext.getString(R.string.compose_editor_bold))
            for (index in listOf(1, 0)) {
                checkNotNull(device.wait(Until.findObject(options), 5_000)).click()
                assertTrue(device.wait(Until.hasObject(By.text("Pin field")), 5_000))
                // Restoring focus for a native control must not count as a field tap.
                runBlocking { web.focusField(1) }
                device.waitForIdle()
                assertFalse(device.hasObject(bold))

                val point =
                    JSONObject(
                        web.evaluate(
                            """
                            (() => {
                                const bounds = ${richField(index)}.getBoundingClientRect();
                                return { x: bounds.left + bounds.width / 2, y: bounds.top + bounds.height / 2,
                                    scale: devicePixelRatio };
                            })()
                            """.trimIndent(),
                        ),
                    )
                val location = IntArray(2)
                scenario.onActivity { web.getLocationOnScreen(location) }
                assertTrue(
                    device.click(
                        location[0] + (point.getDouble("x") * point.getDouble("scale")).toInt(),
                        location[1] + (point.getDouble("y") * point.getDouble("scale")).toInt(),
                    ),
                )
                assertTrue(device.wait(Until.hasObject(bold), 5_000), "Tapping field $index must restore formatting controls")
            }
        }
    }

    @Test
    fun fieldOptionsDisappearAfterDismissingKeyboardAndPressingBack() {
        withEditor { scenario ->
            val web = scenario.awaitEditor()
            runBlocking { web.focusField(1) }

            fun keyboardVisible(): Boolean {
                var visible = false
                scenario.onActivity {
                    visible = ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
                }
                return visible
            }
            waitUntil(message = { "The keyboard did not open for the field" }) { keyboardVisible() }
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val options = By.desc(testContext.getString(R.string.compose_editor_field_options)).enabled(true)
            checkNotNull(device.wait(Until.findObject(options), 5_000)).click()
            val backOptions = By.descStartsWith(testContext.getString(R.string.compose_editor_field_options_title, "Back"))
            assertTrue(device.wait(Until.hasObject(backOptions), 5_000))

            device.pressBack()
            waitUntil(message = { "Back did not dismiss the keyboard" }) { !keyboardVisible() }
            assertTrue(device.wait(Until.hasObject(backOptions), 5_000))
            device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.desc(testContext.getString(R.string.compose_editor_bold))), 5_000))
            captureScreenshot("field-options-closed-after-keyboard-back", web)
            assertTrue(device.wait(Until.gone(backOptions), 5_000), "The closed sheet must not peek below the toolbar")
        }
    }

    @Test
    fun fieldOptionsKeepTheirTargetAndApplyWithoutReloadingFields() {
        withEditor { scenario ->
            val web = scenario.awaitEditor()
            web.setField(0, "Unsaved front")
            web.setField(1, "Retained back")
            runBlocking { web.focusField(1) }
            val caret = assertNotNull(runBlocking { web.captureTarget() })

            fun keyboardVisible(): Boolean {
                var visible = false
                scenario.onActivity {
                    visible = ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
                }
                return visible
            }
            waitUntil(message = { "The keyboard did not open for the field" }) { keyboardVisible() }
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val options = By.desc(testContext.getString(R.string.compose_editor_field_options)).enabled(true)
            val backOptions = By.descStartsWith(testContext.getString(R.string.compose_editor_field_options_title, "Back"))

            fun toggleOptions() = checkNotNull(device.wait(Until.findObject(options), 5_000)).click()

            fun choose(label: Int) =
                checkNotNull(device.wait(Until.findObject(By.descContains(testContext.getString(label)).enabled(true)), 5_000)).click()

            val bounds = checkNotNull(device.wait(Until.findObject(options), 5_000)).visibleBounds
            device.swipe(bounds.left - 20, bounds.centerY(), 20, bounds.centerY(), 15)
            assertEquals(bounds, checkNotNull(device.findObject(options)).visibleBounds)
            device.waitForIdle()
            assertTrue(keyboardVisible(), "Scrolling the toolbar must keep the keyboard visible")
            toggleOptions()
            assertTrue(device.wait(Until.hasObject(backOptions), 5_000))
            val bold = By.desc(testContext.getString(R.string.compose_editor_bold))
            assertTrue(device.wait(Until.gone(bold), 5_000))
            captureScreenshot("field-options", web)
            device.waitForIdle()
            assertTrue(keyboardVisible(), "Opening field options must keep the keyboard visible")
            choose(R.string.compose_editor_field_options_help)
            val pinExplanation = By.text(testContext.getString(R.string.compose_editor_pin_field_summary))
            val languageExplanation = By.text(testContext.getString(R.string.compose_editor_language_hint_summary))
            val scopeExplanation = By.text(testContext.getString(R.string.compose_editor_field_options_help_scope, "Back", "Basic"))
            assertTrue(device.wait(Until.hasObject(pinExplanation), 5_000))
            assertTrue(device.wait(Until.hasObject(languageExplanation), 5_000))
            assertTrue(device.wait(Until.hasObject(scopeExplanation), 5_000))
            assertTrue(keyboardVisible(), "Opening help must keep the keyboard visible")
            assertFalse(scenario.editorState().fields[1].sticky, "Opening help must not toggle pinning")
            assertEquals(null, scenario.editorState().fields[1].languageTag)
            captureScreenshot("field-options-help", web)
            choose(R.string.compose_editor_field_options_help)
            assertTrue(device.wait(Until.gone(pinExplanation), 5_000))
            choose(R.string.compose_editor_field_options_help)
            assertTrue(device.wait(Until.hasObject(pinExplanation), 5_000))
            checkNotNull(device.findObject(backOptions)).click()
            assertTrue(device.wait(Until.gone(pinExplanation), 5_000), "Tapping outside must dismiss help")
            assertTrue(keyboardVisible(), "Dismissing help must keep the keyboard visible")
            assertEquals(caret.bookmark, runBlocking { web.captureTarget() }?.bookmark)
            for ((label, tooltip) in listOf(
                R.string.compose_editor_language_hint to R.string.compose_editor_language_hint_summary,
                R.string.compose_editor_pin_field to R.string.compose_editor_pin_field_summary,
            )) {
                checkNotNull(device.findObject(By.descContains(testContext.getString(label)))).longClick()
                val tooltipText = By.text(testContext.getString(tooltip))
                assertTrue(device.wait(Until.hasObject(tooltipText), 5_000), "Missing tooltip: ${testContext.getString(label)}")
                assertTrue(keyboardVisible(), "Showing a field tooltip must keep the keyboard visible")
                assertTrue(device.wait(Until.gone(tooltipText), 5_000))
            }
            toggleOptions()
            assertTrue(device.wait(Until.gone(backOptions), 5_000))
            assertTrue(device.wait(Until.hasObject(bold), 5_000))
            assertTrue(keyboardVisible(), "Collapsing field options must keep the keyboard visible")
            assertEquals(caret.bookmark, runBlocking { web.captureTarget() }?.bookmark)

            toggleOptions()
            choose(R.string.compose_editor_pin_field)
            waitUntil(message = { "Pin did not apply to Back" }) { scenario.editorState().fields[1].sticky }
            assertFalse(scenario.editorState().fields[0].sticky)
            val pin = By.checkable(true).hasDescendant(By.descContains(testContext.getString(R.string.compose_editor_pin_field)))
            assertTrue(checkNotNull(device.wait(Until.findObject(pin), 5_000)).isChecked)
            runBlocking { web.focusField(0) }
            assertTrue(
                device.wait(
                    Until.hasObject(By.descStartsWith(testContext.getString(R.string.compose_editor_field_options_title, "Front"))),
                    5_000,
                ),
            )
            assertFalse(checkNotNull(device.findObject(pin)).isChecked)
            runBlocking { web.focusField(1) }
            assertTrue(device.wait(Until.hasObject(backOptions), 5_000))
            assertTrue(checkNotNull(device.findObject(pin)).isChecked)
            assertTrue(keyboardVisible(), "Using field options must keep the keyboard visible")
            choose(R.string.compose_editor_language_hint)
            assertTrue(device.wait(Until.hasObject(By.res(testContext.packageName, "locale_dialog_selection_list")), 5_000))

            scenario.recreate()
            val restored = scenario.awaitEditor()
            // Native dialog focus and a different active field must not retarget its result.
            runBlocking { restored.focusField(0) }
            restored.evaluate("window.originalBackInput = ${richField(1)}")
            checkNotNull(device.wait(Until.findObject(By.res(testContext.packageName, "locale_dialog_action_search")), 5_000)).click()
            checkNotNull(device.wait(Until.findObject(By.res(testContext.packageName, "search_src_text")), 5_000)).text =
                Locale.JAPANESE.displayName
            checkNotNull(
                device.wait(
                    Until.findObject(By.res(testContext.packageName, "locale_dialog_fragment_text_view").text(Locale.JAPANESE.displayName)),
                    5_000,
                ),
            ).click()
            waitUntil(message = { "Language picker did not apply to its original field" }) {
                scenario.editorState().fields[1].languageTag == "ja"
            }
            assertEquals(null, scenario.editorState().fields[0].languageTag)
            runBlocking { restored.focusField(1) }

            fun inputHint(): String? {
                var tag: String? = null
                scenario.onActivity {
                    tag =
                        EditorInfo()
                            .also { restored.onCreateInputConnection(it) }
                            .hintLocales
                            ?.get(0)
                            ?.toLanguageTag()
                }
                return tag
            }
            waitUntil(message = { "Android input connection did not receive the language hint" }) { inputHint() == "ja" }
            assertEquals("true", restored.evaluate("window.originalBackInput === ${richField(1)}"))
            assertEquals(listOf("Unsaved front", "Retained back"), runBlocking { restored.snapshot().fields })

            assertTrue(device.wait(Until.hasObject(backOptions), 5_000))
            captureScreenshot("field-options-language", restored)
            choose(R.string.compose_editor_language_hint)
            val localePicker = By.res(testContext.packageName, "locale_dialog_selection_list")
            assertTrue(device.wait(Until.hasObject(localePicker), 5_000))
            device.pressBack()
            assertTrue(device.wait(Until.gone(localePicker), 5_000))
            assertEquals("ja", scenario.editorState().fields[1].languageTag, "Cancel must not reset the language hint")
            choose(R.string.compose_editor_language_hint)
            checkNotNull(device.wait(Until.findObject(By.res("android", "button3")), 5_000)).click()
            waitUntil(message = { "Default did not clear the keyboard language hint" }) {
                scenario.editorState().fields[1].languageTag == null && inputHint() == null
            }
            clickText(testContext.getString(CommonString.save))
            restored.awaitJavascript("${richField(0)}?.textContent === '' && ${richField(1)}?.textContent === 'Retained back'")
            assertEquals(listOf("", "Retained back"), runBlocking { restored.snapshot().fields })
        }
    }

    @Test
    fun deckSelectionUsesTheSharedDialogAndSurvivesRecreation() {
        val destination = col.decks.id("Study::Languages")
        col.decks.newFiltered("Filtered destination")
        withEditor { scenario ->
            val web = scenario.awaitEditor()
            val originalDeck = scenario.editorState().deckId
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val deckList = UiSelector().resourceId("${testContext.packageName}:id/decks")
            // Compose merges the label and selected deck into one accessibility node.
            val deckButton = device.findObject(UiSelector().textStartsWith(testContext.getString(R.string.compose_editor_deck)))
            assertTrue(deckButton.click())
            waitUntil(timeout = 5.seconds, message = { "The shared deck selection dialog did not open" }) {
                device.findObject(deckList).exists()
            }
            waitUntil(timeout = 5.seconds, message = { "The deck list did not finish loading" }) {
                device.findObject(UiSelector().text("Study")).exists()
            }
            captureScreenshot("deck-picker", web)
            assertFalse(device.findObject(UiSelector().text("Filtered destination")).exists())
            assertFalse(device.findObject(UiSelector().text(SelectableDeck.AllDecks.getDisplayName(testContext))).exists())
            checkNotNull(device.findObject(By.res("android", "button2"))).click()
            waitUntil(timeout = 5.seconds, message = { "Cancel did not dismiss deck selection" }) { !device.findObject(deckList).exists() }
            assertEquals(originalDeck, scenario.editorState().deckId)
            assertFalse(scenario.editorState().hasMetadataChanges)

            web.setField(0, "Keep this draft while choosing a deck")
            assertTrue(deckButton.click())
            waitUntil(timeout = 5.seconds, message = { "Deck selection did not reopen" }) { device.findObject(deckList).exists() }
            scenario.recreate()
            val restored = scenario.awaitEditor()
            waitUntil(timeout = 5.seconds, message = { "Deck selection was not restored" }) { device.findObject(deckList).exists() }
            checkNotNull(device.findObject(By.res(testContext.packageName, "action_filter"))).click()
            checkNotNull(device.findObject(By.res(testContext.packageName, "search_src_text"))).text = "Languages"
            waitUntil(timeout = 5.seconds, message = { "Search did not find the subdeck" }) {
                device.hasObject(By.text("Languages"))
            }
            checkNotNull(device.findObject(By.text("Languages").res(testContext.packageName, "deck_text_view"))).click()
            waitUntil(timeout = 5.seconds, message = { "Deck selection did not reach the recreated editor" }) {
                scenario.editorState().deckId == destination
            }
            assertEquals("Study::Languages", scenario.editorState().deckName)
            assertTrue(scenario.editorState().hasMetadataChanges)
            assertEquals("Keep this draft while choosing a deck", runBlocking { restored.snapshot().fields.first() })

            clickText(testContext.getString(CommonString.save))
            waitUntil(timeout = 30.seconds, message = { "Add did not save to the chosen deck" }) {
                runBlocking { withCol { noteCount() == 1 } }
            }
            assertEquals(destination, runBlocking { withCol { getNote(findNotes("").single()).firstCard(this).did } })
        }
    }

    @Test
    fun addingSavesTheSnapshotAndStartsTheNextNoteWithStickyFields() {
        val type = col.notetypes.basic.deepClone()
        type.fields[0].sticky = true
        type.fields[1].sticky = false
        col.notetypes.save(type)

        withEditor { scenario ->
            val web = scenario.awaitEditor()
            web.setField(0, "<b>Retained front</b>")
            web.setField(1, "Clear this back")
            captureScreenshot("phone-add", web)

            runBlocking { web.focusField(0) }
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            device.waitForIdle()
            var sourceButton = checkNotNull(device.findObject(By.desc(testContext.getString(R.string.compose_editor_source))))
            while (!sourceButton.isClickable) sourceButton = checkNotNull(sourceButton.parent)
            waitUntil(timeout = 10.seconds, message = { "The native Edit HTML button did not become enabled for the focused field" }) {
                sourceButton.isEnabled
            }
            val sourceBounds = sourceButton.visibleBounds
            assertFalse(sourceBounds.isEmpty, "The native Edit HTML button is outside the visible toolbar")
            assertTrue(device.click(sourceBounds.centerX(), sourceBounds.centerY()))
            try {
                web.awaitJavascript(
                    """
                    (() => {
                        const source = document.querySelector('.editor-field[data-field-ordinal="0"] .CodeMirror');
                        return source?.offsetHeight > 0 && source.CodeMirror.getValue() === '<b>Retained front</b>';
                    })()
                    """.trimIndent(),
                )
            } finally {
                captureScreenshot("phone-source", web)
            }

            clickText(testContext.getString(CommonString.save))

            waitUntil(timeout = 30.seconds, message = { "Add did not persist a note" }) {
                runBlocking { withCol { noteCount() == 1 } }
            }
            web.awaitJavascript("${richField(1)}?.textContent === '' && ${richField(0)}?.isContentEditable === true")
            assertEquals(listOf("<b>Retained front</b>", ""), runBlocking { web.snapshot().fields })
            assertFalse(runBlocking { web.snapshot().hasChanges })
            assertTrue(scenario.editorState().isAdding)
            assertEquals(1, scenario.editorState().generation)
            val savedFields = runBlocking { withCol { getNote(findNotes("").single()).fields.toList() } }
            assertEquals(listOf("<b>Retained front</b>", "Clear this back"), savedFields)
        }
    }

    @Test
    fun editingSavesTheNoteAndClosesTheActivity() {
        val note = addNoteUsingBasicNoteType("Original front", "Original back")
        val arguments =
            Bundle().apply {
                putInt(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.EDIT.value)
                putLong(NoteEditorFragment.EXTRA_CARD_ID, note.firstCard(col).id)
            }
        withEditor(arguments) { scenario ->
            val web = scenario.awaitEditor()
            web.setField(0, "Edited front 日本")
            web.setField(1, "<i>Edited back</i>")

            clickText(testContext.getString(CommonString.save))

            waitUntil(timeout = 30.seconds, message = { "Edit Save did not close the activity" }) {
                scenario.state == Lifecycle.State.DESTROYED
            }
            val savedFields = runBlocking { withCol { getNote(note.id).fields.toList() } }
            assertEquals(listOf("Edited front 日本", "<i>Edited back</i>"), savedFields)
            assertEquals(1, runBlocking { withCol { noteCount() } })
        }
    }

    @Test
    fun backDisablesEditingAndSaveUntilItsSnapshotFinishes() {
        withEditor { scenario ->
            val web = scenario.awaitEditor()
            web.setField(0, "Unsaved while closing")
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val saveBounds = device.findObject(UiSelector().text(testContext.getString(CommonString.save))).bounds
            web.evaluate(
                """
                (() => {
                    const request = AnkiEditor.request.bind(AnkiEditor);
                    AnkiEditor.request = (id, method, args) => {
                        if (method === 'snapshot' && !window.backSnapshotHeld && ${richField(0)}.isContentEditable === false) {
                            window.backSnapshotHeld = true;
                            window.releaseBackSnapshot = () => request(id, method, args);
                            return;
                        }
                        return request(id, method, args);
                    };
                })();
                """.trimIndent(),
            )

            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

            web.awaitJavascript("window.backSnapshotHeld === true")
            assertEquals("false", web.evaluate("${richField(0)}.isContentEditable"))
            // Tap the control's position: its Text child does not inherit the Compose
            // button's disabled semantics. Avoid waiting for an idle busy spinner.
            assertTrue(device.click(saveBounds.centerX(), saveBounds.centerY()))
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertFalse(scenario.editorState().isSaving)
            assertEquals(0, runBlocking { withCol { noteCount() } })

            web.evaluate("window.releaseBackSnapshot()")

            val discardMessage = testContext.getString(R.string.compose_editor_discard)
            waitUntil(timeout = 5.seconds, message = { "Back did not ask before discarding the dirty snapshot" }) {
                device.findObject(UiSelector().text(discardMessage)).exists()
            }
            clickText(testContext.getString(CommonString.dialog_cancel))
            web.awaitJavascript("${richField(0)}.isContentEditable === true")
            assertEquals("Unsaved while closing", runBlocking { web.snapshot().fields.first() })
            assertEquals(0, runBlocking { withCol { noteCount() } })
        }
    }

    @Test
    fun recreationRestoresFieldsDeckTagsAndTheDirtyBaseline() {
        val deckId = col.decks.id("Restored draft deck")
        withEditor { scenario ->
            val web = scenario.awaitEditor()
            web.setField(0, "Unsaved front 日本")
            web.setField(1, "<b>Unsaved back</b>")
            val model = scenario.editorModel()
            runBlocking {
                withContext(Dispatchers.Main) {
                    model.selectDeck(deckId)
                    model.setTags(listOf("draft-tag", "日本語"))
                }
                web.updateHostState(withContext(Dispatchers.Main) { model.hostState() })
            }
            val session = scenario.editorState().sessionId

            scenario.recreate()

            val restored = scenario.awaitEditor()
            assertNotSame(web, restored)
            assertEquals(listOf("Unsaved front 日本", "<b>Unsaved back</b>"), runBlocking { restored.snapshot().fields })
            assertTrue(runBlocking { restored.snapshot().hasChanges })
            assertEquals(session, scenario.editorState().sessionId)
            assertEquals(deckId, scenario.editorState().deckId)
            assertEquals(listOf("draft-tag", "日本語"), scenario.editorState().tags)
            assertTrue(scenario.editorState().hasMetadataChanges)
            assertEquals(0, runBlocking { withCol { noteCount() } })
        }
    }

    @Test
    fun freshActivityDiscoversTheDraftAndRestoresItsCompleteState() {
        val deckId = col.decks.id("Recovered draft deck")
        lateinit var previousModel: EditorViewModel
        lateinit var session: String
        withEditor { scenario ->
            val web = scenario.awaitEditor()
            previousModel = scenario.editorModel()
            web.setField(0, "Recovered front 日本")
            web.setField(1, "<b>Recovered back</b>")
            runBlocking {
                withContext(Dispatchers.Main) {
                    previousModel.selectDeck(deckId)
                    previousModel.setTags(listOf("recovered-tag", "日本語"))
                }
                web.updateHostState(withContext(Dispatchers.Main) { previousModel.hostState() })
                previousModel.rememberDraft()
            }
            session = scenario.editorState().sessionId
            // Closing the scenario finishes the host without taking its explicit discard path.
        }

        withEditor { scenario ->
            val web = scenario.awaitEditor()
            assertNotSame(previousModel, scenario.editorModel())
            assertEquals(session, scenario.editorState().sessionId)
            assertEquals(listOf("Recovered front 日本", "<b>Recovered back</b>"), runBlocking { web.snapshot().fields })
            assertTrue(runBlocking { web.snapshot().hasChanges })
            assertEquals(deckId, scenario.editorState().deckId)
            assertEquals(listOf("recovered-tag", "日本語"), scenario.editorState().tags)
            assertTrue(scenario.editorState().hasMetadataChanges)
            assertEquals(0, runBlocking { withCol { noteCount() } })
        }
    }

    @Test
    fun tabletPreviewRendersUnsavedChangesAndKeepsShowingTheAnswer() {
        withEditor { scenario ->
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            waitUntil(timeout = 30.seconds, message = { "Editor did not enter landscape" }) {
                var landscape = false
                scenario.onActivity { landscape = it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
                landscape
            }
            var windowWidth = 0
            var smallestWidth = 0
            scenario.onActivity {
                windowWidth = it.resources.configuration.screenWidthDp
                smallestWidth = it.resources.configuration.smallestScreenWidthDp
            }
            assumeTrue("Preview needs a tablet window with smallest width at least 600 dp", windowWidth >= 600 && smallestWidth >= 600)
            val editor = scenario.awaitEditor()
            runBlocking { editor.focusField(0) }
            editor.setField(0, "Preview unsaved front")
            editor.setField(1, "Preview first answer")
            var cardWebView: WebView? = null
            waitUntil(timeout = 30.seconds, message = { "The side-by-side preview WebView did not appear" }) {
                scenario.onActivity { activity ->
                    cardWebView =
                        activity.window.decorView
                            .webViews()
                            .firstOrNull { it !is WebEditorView }
                }
                cardWebView != null
            }
            val preview = checkNotNull(cardWebView)
            scenario.onActivity { activity ->
                val views =
                    activity.window.decorView.webViews().map { web ->
                        val position = IntArray(2).also(web::getLocationOnScreen)
                        "${web.javaClass.simpleName}@${System.identityHashCode(web)} shown=${web.isShown} " +
                            "bounds=${position.toList()} ${web.width}x${web.height} url=${web.url}"
                    }
                android.util.Log.i("ComposeEditorTest", "Preview views=$views fragments=${activity.supportFragmentManager.fragments}")
            }
            preview.awaitJavascript("document.getElementById('qa')?.textContent.includes('Preview unsaved front') === true")
            preview.awaitJavascript("getComputedStyle(document.getElementById('qa')).opacity === '1'")
            captureScreenshot("tablet-preview", editor, preview)
            editor.setField(0, "Preview updated front")
            preview.awaitJavascript("document.getElementById('qa')?.textContent.includes('Preview updated front') === true")
            captureScreenshot("tablet-updated", editor, preview)
            clickText(testContext.getString(CommonString.show_answer))
            preview.awaitJavascript("document.getElementById('qa')?.textContent.includes('Preview first answer') === true")

            editor.setField(1, "Preview updated answer")

            preview.awaitJavascript("document.getElementById('qa')?.textContent.includes('Preview updated answer') === true")
            captureScreenshot("tablet-answer", editor, preview)
            assertEquals(0, runBlocking { withCol { noteCount() } })
            assertTrue(runBlocking { editor.snapshot().hasChanges })
        }
    }

    private fun withEditor(
        arguments: Bundle = NoteEditorFragment.addNoteArgs(),
        block: (ActivityScenario<ComposeNoteEditorActivity>) -> Unit,
    ) {
        val intent = Intent(testContext, ComposeNoteEditorActivity::class.java).putExtras(arguments)
        ActivityScenario.launch<ComposeNoteEditorActivity>(intent).use(block)
    }

    private fun ActivityScenario<ComposeNoteEditorActivity>.awaitEditor(): WebEditorView {
        var editor: WebEditorView? = null
        waitUntil(timeout = 30.seconds, message = { "The production field editor did not appear" }) {
            onActivity { activity ->
                editor =
                    activity.window.decorView
                        .webViews()
                        .filterIsInstance<WebEditorView>()
                        .firstOrNull()
            }
            editor != null
        }
        return checkNotNull(editor).also {
            it.awaitJavascript("${richField(0)}?.isContentEditable === true")
            // Mounted fields precede completion of loadDocument; serialize past that load.
            runBlocking { it.snapshot() }
            // A mounted editor can still be clipped by a zero-height viewport.
            // Require the field to be visible and reachable, as a user's tap would be.
            it.awaitJavascript(
                """
                (() => {
                    const field = ${richField(0)};
                    const bounds = field.getBoundingClientRect();
                    if (bounds.width <= 0 || bounds.height <= 0 || bounds.top < 0 || bounds.bottom > innerHeight) return false;
                    const hit = document.elementFromPoint(bounds.left + bounds.width / 2, bounds.top + bounds.height / 2);
                    return hit === field.getRootNode().host;
                })()
                """.trimIndent(),
            )
        }
    }

    private fun ActivityScenario<ComposeNoteEditorActivity>.editorModel(): EditorViewModel {
        lateinit var model: EditorViewModel
        onActivity { model = ViewModelProvider(it)[EditorViewModel::class.java] }
        return model
    }

    private fun ActivityScenario<ComposeNoteEditorActivity>.editorState(): EditorState {
        lateinit var state: EditorState
        onActivity { state = checkNotNull(ViewModelProvider(it)[EditorViewModel::class.java].state.value) }
        return state
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

    /** Upstream RichTextInput mounts the real contenteditable inside an open shadow root. */
    private fun richField(index: Int): String =
        "document.querySelector('.field-container[data-index=\"$index\"] .rich-text-editable')?.shadowRoot?.querySelector('anki-editable')"

    /** Optional instrumentation artifacts; no production debug UI or pauses are required. */
    private fun captureScreenshot(
        name: String,
        vararg webViews: WebView,
    ) {
        val artifactDir = InstrumentationRegistry.getArguments().getString("artifactDir") ?: return
        val directory = File(testContext.filesDir, artifactDir)
        check(directory.isDirectory || directory.mkdirs())
        // DOM assertions can finish before Chromium submits the corresponding frame.
        val rendered = CountDownLatch(webViews.size)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webViews.forEach { web ->
                web.postVisualStateCallback(
                    0,
                    object : WebView.VisualStateCallback() {
                        override fun onComplete(requestId: Long) {
                            web.postOnAnimation { web.postOnAnimation { rendered.countDown() } }
                        }
                    },
                )
            }
        }
        assertTrue(rendered.await(30, TimeUnit.SECONDS), "WebView did not render the screenshot state")
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.waitForIdle()
        // Capture only the actual display, without forcing WebView invalidation or drawing.
        assertTrue(device.takeScreenshot(File(directory, "$name.png")))
        device.dumpWindowHierarchy(File(directory, "$name.xml"))
        webViews.forEachIndexed { index, web ->
            File(directory, "$name-web$index.json").writeText(
                web.evaluate(
                    """
                    JSON.stringify({
                        url: location.href, viewport: [innerWidth, innerHeight],
                        body: document.body.innerHTML,
                        qa: (() => {
                            const qa = document.getElementById('qa');
                            if (!qa) return null;
                            const style = getComputedStyle(qa);
                            return {html: qa.innerHTML, bounds: qa.getBoundingClientRect().toJSON(),
                                visibility: style.visibility, display: style.display, opacity: style.opacity};
                        })()
                    })
                    """.trimIndent(),
                ),
            )
        }
    }

    private fun clickText(text: String) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val button = device.findObject(UiSelector().text(text).enabled(true))
        waitUntil(timeout = 30.seconds, message = { "Enabled button not found: $text" }) { button.exists() }
        assertTrue(button.click(), "Unable to click: $text")
    }

    private fun View.webViews(): List<WebView> =
        when (this) {
            is WebView -> listOf(this)
            is ViewGroup -> (0 until childCount).flatMap { getChildAt(it).webViews() }
            else -> emptyList()
        }
}
