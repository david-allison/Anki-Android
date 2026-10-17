// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.app.Activity
import android.app.Instrumentation
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.WindowInsetsAnimation
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.core.os.BundleCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CommonString
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.NoteEditorFragment
import com.ichi2.anki.NoteEditorFragment.Companion.NoteEditorCaller
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.TestUtils
import com.ichi2.anki.common.utils.ext.AddingDefaultsMode
import com.ichi2.anki.common.utils.ext.addingDefaultsMode
import com.ichi2.anki.compat.CompatHelper.Companion.getSerializableExtraCompat
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.multimedia.MultimediaActivity
import com.ichi2.anki.multimedia.MultimediaActivityExtra
import com.ichi2.anki.multimedia.MultimediaArgsStorage
import com.ichi2.anki.multimedia.MultimediaBottomSheet
import com.ichi2.anki.multimedia.MultimediaResultContract
import com.ichi2.anki.multimediacard.fields.AudioRecordingField
import com.ichi2.anki.noteeditor.compose.preview.EditorPreviewFragment
import com.ichi2.anki.noteeditor.web.WebEditorAction
import com.ichi2.anki.noteeditor.web.WebEditorTarget
import com.ichi2.anki.noteeditor.web.WebEditorView
import com.ichi2.anki.pages.ImageOcclusion
import com.ichi2.anki.pages.viewmodel.ImageOcclusionArgs
import com.ichi2.anki.pages.viewmodel.ImageOcclusionViewModel
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.GrantStoragePermission.storagePermission
import com.ichi2.anki.testutil.awaitJavascript
import com.ichi2.anki.testutil.ensureWebViewIsSupported
import com.ichi2.anki.testutil.evaluate
import com.ichi2.anki.testutil.grantPermissions
import com.ichi2.anki.testutil.notificationPermission
import com.ichi2.anki.testutil.waitUntil
import com.ichi2.anki.testutil.withTestKeyboard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
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
    fun scrollingFieldsCollapsesTheHeaderAndKeepsSaveReachable() {
        withEditor { scenario ->
            val web = scenario.awaitEditor()
            val content = (1..100).joinToString("") { "<div>Line $it of a long field</div>" }
            web.setField(0, content)
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val deck = By.text(testContext.getString(R.string.compose_editor_deck))
            val save = By.desc(testContext.getString(CommonString.save))
            assertNotNull(device.wait(Until.findObject(deck), 5_000))
            val saveBounds = checkNotNull(device.findObject(save)).visibleBounds
            captureScreenshot("scroll-header-expanded", web)

            fun swipe(down: Boolean) {
                val location = IntArray(2)
                var x = 0
                var top = 0
                var bottom = 0
                scenario.onActivity {
                    web.getLocationOnScreen(location)
                    x = location[0] + web.width / 2
                    top = location[1] + web.height / 4
                    bottom = location[1] + web.height * 3 / 4
                }
                device.swipe(x, if (down) bottom else top, x, if (down) top else bottom, 30)
            }
            swipe(down = true)
            assertTrue(device.wait(Until.gone(deck), 5_000), "Deck and Type should hide when scrolling down")
            assertEquals(saveBounds, checkNotNull(device.findObject(save)).visibleBounds)
            captureScreenshot("scroll-header-collapsed", web)
            swipe(down = false)
            assertNotNull(device.wait(Until.findObject(deck), 5_000), "Scrolling up should restore the controls")
            assertEquals(saveBounds, checkNotNull(device.findObject(save)).visibleBounds)
            captureScreenshot("scroll-header-restored", web)
            assertEquals(content, runBlocking { web.snapshot().fields.first() })
        }
    }

    @Test
    fun undoAndRedoUseTheSelectedFieldsHistory() =
        withTestKeyboard {
            withEditor { scenario ->
                val web = scenario.awaitEditor()
                waitUntil(message = { "Initial editor focus did not finish" }) { scenario.editorModel().hasAttachedEditor }
                web.setField(0, "Front")
                web.setField(1, "Back")
                runBlocking {
                    web.focusField(0)
                    assertTrue(web.execute(WebEditorAction.INSERT_TEXT, value = " first"))
                    web.focusField(1)
                    assertTrue(web.execute(WebEditorAction.INSERT_TEXT, value = " second"))
                    web.focusField(0)
                }
                val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

                // History arrives over the bridge before Compose publishes the enabled state.
                fun command(label: Int) =
                    checkNotNull(device.wait(Until.findObject(By.desc(testContext.getString(label)).enabled(true)), 5_000)) {
                        "${testContext.getString(label)} did not become enabled; fields=${runBlocking { web.snapshot().fields }}"
                    }.click()
                command(CommonString.undo)
                waitUntil(message = { "Undo must change Front and leave Back alone; got ${runBlocking { web.snapshot().fields }}" }) {
                    runBlocking { web.snapshot().fields } == listOf("Front", "Back second")
                }
                runBlocking {
                    web.focusField(1)
                    assertTrue(web.execute(WebEditorAction.INSERT_TEXT, value = " third"))
                    web.focusField(0)
                }
                command(CommonString.redo)
                waitUntil(message = { "An edit to Back must not discard Front's redo" }) {
                    runBlocking { web.snapshot().fields } == listOf("Front first", "Back second third")
                }
                command(R.string.compose_editor_source)
                web.awaitJavascript("document.querySelectorAll('.CodeMirror').length === 1")
                runBlocking {
                    assertTrue(web.execute(WebEditorAction.INSERT_TEXT, value = " source"))
                }
                command(CommonString.undo)
                waitUntil(message = { "HTML mode undo must also leave Back alone" }) {
                    runBlocking { web.snapshot().fields } == listOf("Front first", "Back second third")
                }
            }
        }

    @Test
    fun attachMediaUsesTheSharedSheetAndRetainsItsDestination() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val launched = AtomicReference<MultimediaActivityExtra>()
        val monitor =
            object : Instrumentation.ActivityMonitor() {
                override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                    if (intent.component?.className != MultimediaActivity::class.java.name) return null
                    val file = checkNotNull(intent.getSerializableExtraCompat<File>(MultimediaActivity.EXTRA_FRAGMENT_ARGS))
                    launched.set(MultimediaArgsStorage.create(testContext).read(file))
                    file.delete()
                    return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
                }
            }
        instrumentation.addMonitor(monitor)
        try {
            withEditor { scenario ->
                var web = scenario.awaitEditor()
                web.setField(0, "Front text.")
                web.setField(1, "Back text.")
                val state = scenario.editorState()
                val model = scenario.editorModel()
                runBlocking {
                    withContext(Dispatchers.Main) {
                        for (index in state.fields.indices) {
                            model.setFieldToolbar(
                                EditorFieldTarget(state.sessionId, state.generation, index),
                                EditorFieldToolbar(buttonOrder = listOf(EditorToolbarButton.MEDIA.name)),
                            )
                        }
                    }
                    web.focusField(0)
                }
                val device = UiDevice.getInstance(instrumentation)

                fun openSheet(quickAction: Boolean = false) {
                    device.waitForIdle()
                    if (quickAction) {
                        assertEquals(
                            listOf("pin", "attachMedia"),
                            scenario
                                .editorState()
                                .fields[1]
                                .toolbar.quickActions,
                        )
                        web.evaluate("document.querySelectorAll('.field-container[data-index=\"1\"] .quick-action')[1].click()")
                    } else {
                        checkNotNull(
                            device.wait(Until.findObject(By.desc(testContext.getString(R.string.compose_editor_media))), 5_000),
                        ).click()
                    }
                    assertNotNull(device.wait(Until.findObject(By.res(testContext.packageName, "multimedia_action_drawing")), 5_000))
                    scenario.onActivity { activity ->
                        assertTrue(activity.supportFragmentManager.fragments.any { it is MultimediaBottomSheet })
                    }
                }
                openSheet(quickAction = true)
                assertEquals(0, runBlocking { web.captureTarget() }?.field, "Opening an inactive field's quick action must preserve focus")
                captureScreenshot("native-media-sheet", web)
                scenario.recreate()
                web = scenario.awaitEditor()
                assertNotNull(device.wait(Until.findObject(By.res(testContext.packageName, "multimedia_action_image")), 5_000))
                runBlocking { web.focusField(0) }
                device.findObject(By.res(testContext.packageName, "multimedia_action_image")).click()
                waitUntil(message = { "Gallery was not launched" }) { launched.get() != null }
                assertEquals(1, launched.get().index, "Recreating the sheet or moving focus must not redirect its attachment")
                assertEquals(listOf("Front text.", "Back text."), runBlocking { web.snapshot().fields })
                // Cancellation must allow the next attachment to use the newly selected field.
                launched.set(null)
                openSheet()
                device.findObject(By.res(testContext.packageName, "multimedia_action_drawing")).click()
                waitUntil(message = { "Drawing was not launched" }) { launched.get() != null }
                assertEquals(0, launched.get().index)
            }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 26) // Intercept activity launches without requesting microphone access.
    fun recordingReleasesWebViewFocusAndPreservesItsSelection() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val editor = AtomicReference<WebEditorView>()
        val focusedAtLaunch = CopyOnWriteArrayList<Boolean>()
        val recording = File(testContext.cacheDir, "selection-recording.wav").apply { writeBytes(ByteArray(44)) }
        val monitor =
            object : Instrumentation.ActivityMonitor() {
                override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                    if (intent.component?.className != MultimediaActivity::class.java.name) return null
                    val file = checkNotNull(intent.getSerializableExtraCompat<File>(MultimediaActivity.EXTRA_FRAGMENT_ARGS))
                    val destination = MultimediaArgsStorage.create(testContext).read(file)
                    file.delete()
                    assertEquals(1, destination.index)
                    focusedAtLaunch.add(editor.get().hasFocus())
                    if (focusedAtLaunch.size != 2) return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
                    return Instrumentation.ActivityResult(
                        Activity.RESULT_OK,
                        Intent()
                            .putExtra(MultimediaResultContract.EXTRA_FIELD_INDEX, destination.index)
                            .putExtra(MultimediaResultContract.EXTRA_FIELD, AudioRecordingField().apply { mediaFile = recording }),
                    )
                }
            }
        instrumentation.addMonitor(monitor)
        try {
            withEditor { scenario ->
                val web = scenario.awaitEditor().also(editor::set)
                web.setField(0, "Front remains unchanged")
                web.setField(1, "Before middle after")
                val state = scenario.editorState()
                val model = scenario.editorModel()
                runBlocking {
                    withContext(Dispatchers.Main) {
                        model.setFieldToolbar(
                            EditorFieldTarget(state.sessionId, state.generation, 1),
                            EditorFieldToolbar(buttonOrder = listOf(EditorToolbarButton.RECORD_AUDIO.name)),
                        )
                    }
                    web.focusField(1)
                }
                web.evaluate(
                    """
                    (() => {
                        const field = ${richField(1)};
                        const text = field.firstChild;
                        field.getRootNode().getSelection().setBaseAndExtent(text, 7, text, 13);
                    })()
                    """.trimIndent(),
                )
                waitUntil(message = { "The field keyboard did not open" }) { scenario.keyboardVisible() }
                val target = assertNotNull(runBlocking { web.captureTarget() })
                val device = UiDevice.getInstance(instrumentation)
                val microphone = By.desc(testContext.getString(CommonString.multimedia_editor_popup_audio))
                checkNotNull(device.wait(Until.findObject(microphone), 5_000)).click()
                waitUntil(message = { "The recorder was not opened" }) { focusedAtLaunch.isNotEmpty() }
                assertFalse(focusedAtLaunch.first(), "Release native cursor handles before the activity transition starts")
                waitUntil(message = { "Cancelling the recorder did not restore the keyboard" }) { scenario.keyboardVisible() }
                assertEquals(target, runBlocking { web.captureTarget() }, "Cancelling must preserve the selected text")
                assertEquals(listOf("Front remains unchanged", "Before middle after"), runBlocking { web.snapshot().fields })

                device.pressBack()
                waitUntil(message = { "The field keyboard did not close" }) { !scenario.keyboardVisible() }
                checkNotNull(device.wait(Until.findObject(microphone), 5_000)).click()
                waitUntil(message = { "The recording did not replace the captured selection" }) {
                    runBlocking { web.snapshot().fields } ==
                        listOf("Front remains unchanged", "Before [sound:selection-recording.wav] after")
                }
                assertEquals(listOf(false, false), focusedAtLaunch)
                waitUntil(message = { "A returned recording opened the previously closed keyboard" }) { !scenario.keyboardVisible() }

                web.setField(1, "")
                runBlocking {
                    web.focusField(1)
                    assertTrue(web.execute(WebEditorAction.BOLD, web.captureTarget()))
                }
                waitUntil(message = { "The field keyboard did not reopen" }) { scenario.keyboardVisible() }
                checkNotNull(device.wait(Until.findObject(microphone), 5_000)).click()
                waitUntil(message = { "The recorder did not open again" }) { focusedAtLaunch.size == 3 }
                waitUntil(message = { "Cancelling the recorder did not restore keyboard focus" }) {
                    var focused = false
                    scenario.onActivity { focused = web.hasFocus() }
                    focused && scenario.keyboardVisible()
                }
                device.pressKeyCode(KeyEvent.KEYCODE_B)
                web.awaitJavascript("${richField(1)}.querySelector('b,strong')?.textContent.toLowerCase() === 'b'")
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            recording.delete()
        }
    }

    @Test
    fun pinningKeepsControlsVisuallyEnabledWhileGuardingSave() {
        withEditor { scenario ->
            val web = scenario.awaitEditor()
            web.setField(0, "Front text")
            web.setField(1, "Back text")
            runBlocking { web.focusField(1) }
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            device.openFieldOptions()
            val pin = checkNotNull(device.wait(Until.findObject(By.text(testContext.getString(R.string.compose_editor_pin_field))), 5_000))
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val holdingCollection =
                CoroutineScope(Dispatchers.IO).async {
                    withCol {
                        entered.countDown()
                        check(release.await(30, TimeUnit.SECONDS)) { "The pin test did not release the collection" }
                    }
                }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                pin.click()
                val drawn = CountDownLatch(1)
                scenario.onActivity { activity ->
                    val decor = activity.window.decorView
                    decor.postOnAnimation { decor.postOnAnimation { drawn.countDown() } }
                }
                assertTrue(drawn.await(5, TimeUnit.SECONDS))
                var save = checkNotNull(device.findObject(By.desc(testContext.getString(CommonString.save))))
                while (!save.isClickable) save = checkNotNull(save.parent)
                var pinButton = checkNotNull(device.findObject(By.text(testContext.getString(R.string.compose_editor_pin_field))))
                while (!pinButton.isCheckable) pinButton = checkNotNull(pinButton.parent)
                assertTrue(save.isEnabled, "Pinning must not grey out the editor controls")
                assertTrue(pinButton.isEnabled, "The pin control must retain its normal appearance")
                save.click()
            } finally {
                release.countDown()
                runBlocking { holdingCollection.await() }
            }
            waitUntil(message = { "Pin did not finish updating Back" }) { scenario.editorState().fields[1].sticky }
            assertEquals(0, runBlocking { withCol { noteCount() } }, "Save must not race an in-flight metadata change")
            assertEquals(listOf("Front text", "Back text"), runBlocking { web.snapshot().fields })
            captureScreenshot("pinned-with-enabled-controls", web)
        }
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
    fun fontSizePersistsAcrossRecreationWithoutChangingTheDraft() {
        withEditor { scenario ->
            val web = scenario.awaitEditor()
            // Seed an unfocused draft so Gboard does not start composing the synthetic input.
            web.evaluate("${richField(0)}.blur()")
            scenario.onActivity {
                web.clearFocus()
                WindowCompat.getInsetsController(it.window, web).hide(WindowInsetsCompat.Type.ime())
            }
            web.setField(0, "Unsaved <b>question</b>")
            val before = runBlocking { web.snapshot().fields }
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val expectedSettings = scenario.editorState().settings.copy(fontSize = 28)
            checkNotNull(
                device.wait(Until.findObject(By.desc(testContext.getString(R.string.compose_editor_options)).enabled(true)), 5_000),
            ).click()
            clickText(testContext.getString(CommonString.menu_font_size))
            checkNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5_000)).text = "28"
            clickText(testContext.getString(CommonString.dialog_ok))
            waitUntil(message = { "The global font size did not persist" }) {
                scenario.editorState().settings == expectedSettings
            }
            assertEquals(before, runBlocking { web.snapshot().fields })
            assertFalse(scenario.editorState().hasMetadataChanges)
            scenario.recreate()
            val restored = scenario.awaitEditor()
            assertEquals(before, runBlocking { restored.snapshot().fields })
            assertEquals(
                expectedSettings,
                scenario.editorState().settings,
            )
            assertEquals(0, runBlocking { withCol { noteCount() } })
        }
    }

    @Test
    fun boldOpensKeyboardAndPreservesTypingFormatInTheSelectedField() {
        withEditor { scenario ->
            val web = scenario.awaitEditor()
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            web.setField(0, "Leave Front unchanged")
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
            checkNotNull(device.wait(Until.findObject(bold), 5_000)).click()
            waitUntil(message = { "Tapping Bold must reopen the keyboard without another field tap" }) { keyboardVisible() }
            waitUntil(message = { "Bold must remain checked after the keyboard opens" }) {
                val icon = device.findObject(bold) ?: return@waitUntil false
                (if (icon.isCheckable) icon else icon.parent)?.isChecked == true
            }
            // Send real key events to the focused view; a bridge command would restore selection itself.
            device.pressKeyCode(KeyEvent.KEYCODE_B)
            web.awaitJavascript("${richField(1)}.querySelector('b,strong')?.textContent.toLowerCase() === 'b'")
            assertEquals("Leave Front unchanged", runBlocking { web.snapshot().fields[0] })
            assertEquals(1, runBlocking { web.captureTarget() }?.field)
            assertTrue(keyboardVisible(), "Typing bold text must keep the keyboard open")

            // Finish Gboard's composing word before changing formatting.
            device.pressKeyCode(KeyEvent.KEYCODE_SPACE)
            waitUntil(message = { "Committing the word must re-enable Bold" }) {
                val icon = device.findObject(bold) ?: return@waitUntil false
                (if (icon.isCheckable) icon else icon.parent)?.isEnabled == true
            }
            device.findObject(bold).click()
            waitUntil(message = { "Bold must turn off while the keyboard remains open" }) {
                val icon = device.findObject(bold) ?: return@waitUntil false
                (if (icon.isCheckable) icon else icon.parent)?.isChecked == false && keyboardVisible()
            }
            device.pressKeyCode(KeyEvent.KEYCODE_P)
            web.awaitJavascript(
                "${richField(1)}.textContent.toLowerCase().replace(/\\s/g, '') === 'bp' && " +
                    "${richField(1)}.querySelector('b,strong')?.textContent.trim().toLowerCase() === 'b'",
            )
            captureScreenshot("bold-opens-keyboard-and-keeps-format", web)
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
    fun htmlQuickActionTargetsTheTappedField() {
        withEditor { scenario ->
            val web = scenario.awaitEditor()
            val current = scenario.editorState()
            web.setField(0, "Leave Front unchanged")
            web.setField(1, "<b>Back HTML</b>")
            runBlocking {
                withContext(Dispatchers.Main) {
                    scenario.editorModel().setFieldToolbar(
                        EditorFieldTarget(current.sessionId, current.generation, 1),
                        current.fields[1].toolbar.copy(quickActions = listOf("editHtml")),
                    )
                }
                web.focusField(0)
            }
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            // Gboard can resume composing the last word when an existing field receives focus.
            web.evaluate("${richField(0)}.dispatchEvent(new CompositionEvent('compositionstart', { bubbles: true, composed: true }))")
            val selector = ".field-container[data-index='1'] .quick-action"

            fun tapHtmlAction() {
                web.awaitJavascript("document.querySelector(\"$selector\") !== null")
                val bounds = JSONObject(web.evaluate("document.querySelector(\"$selector\").getBoundingClientRect().toJSON()"))
                val ratio = web.evaluate("devicePixelRatio").toDouble()
                val location = IntArray(2)
                scenario.onActivity { web.getLocationOnScreen(location) }
                device.click(
                    location[0] + ((bounds.getDouble("x") + bounds.getDouble("width") / 2) * ratio).toInt(),
                    location[1] + ((bounds.getDouble("y") + bounds.getDouble("height") / 2) * ratio).toInt(),
                )
            }
            tapHtmlAction()
            web.awaitJavascript(
                "document.querySelector('.editor-field[data-field-ordinal=\"1\"] .CodeMirror')?.CodeMirror.hasFocus() === true",
            )
            assertEquals(1, runBlocking { web.captureTarget() }?.field)
            assertEquals(listOf("Leave Front unchanged", "<b>Back HTML</b>"), runBlocking { web.snapshot().fields })
            captureScreenshot("html-quick-action-back", web)
            tapHtmlAction()
            web.awaitJavascript(
                "document.querySelector('.editor-field[data-field-ordinal=\"1\"] .CodeMirror').getBoundingClientRect().height === 0",
            )
            assertEquals(listOf("Leave Front unchanged", "<b>Back HTML</b>"), runBlocking { web.snapshot().fields })
        }
    }

    @Test
    fun fieldControlsAlignWithAppBarButtons() {
        withEditor { scenario ->
            val web = scenario.awaitEditor()
            val current = scenario.editorState()
            runBlocking {
                withContext(Dispatchers.Main) {
                    scenario.editorModel().setFieldToolbar(
                        EditorFieldTarget(current.sessionId, current.generation, 1),
                        current.fields[1].toolbar.copy(quickActions = listOf("pin", "gallery")),
                    )
                }
            }
            web.awaitJavascript("document.querySelectorAll('.field-container')[1].querySelectorAll('.quick-action').length === 2")
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val back =
                checkNotNull(
                    device.wait(
                        Until.findObject(By.desc(testContext.getString(androidx.appcompat.R.string.abc_action_bar_up_description))),
                        5_000,
                    ),
                )
            val location = IntArray(2)
            scenario.onActivity { web.getLocationOnScreen(location) }
            val geometry =
                JSONObject(
                    web.evaluate(
                        """
                        ({
                            ratio: devicePixelRatio,
                            viewport: innerWidth,
                            root: document.querySelector('.fields-only').getBoundingClientRect().width,
                            fields: [...document.querySelectorAll('.field-container')].map(field => {
                                const collapse = field.querySelector('.collapse-badge').getBoundingClientRect();
                                const source = field.querySelector('.plain-text-badge');
                                const border = field.querySelector('.editor-field').getBoundingClientRect();
                                return {collapse: collapse.x + collapse.width / 2, sourceHidden: getComputedStyle(source).display === "none",
                                    left: border.left, right: border.right};
                            })
                        })
                        """.trimIndent(),
                    ),
                )
            val ratio = geometry.getDouble("ratio")
            // WebView rounds its CSS viewport at fractional densities; allow 1dp against native bounds.
            val fields = geometry.getJSONArray("fields")
            assertEquals(2, fields.length())
            for (index in 0 until fields.length()) {
                val field = fields.getJSONObject(index)
                assertEquals(
                    back.visibleCenter.x.toDouble(),
                    location[0] + ratio * field.getDouble("collapse"),
                    ratio,
                    "Field $index collapse alignment",
                )
                assertTrue(field.getBoolean("sourceHidden"), "HTML editing must be an optional quick action")
                assertEquals(16.0, field.getDouble("left"), 0.5, "Field $index start margin")
                assertEquals(16.0, geometry.getDouble("root") - field.getDouble("right"), 0.5, "Field $index end margin")
            }
            captureScreenshot("field_controls_aligned_with_app_bar", web)
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
            device.openEditorOptions()
            clickText(testContext.getString(R.string.compose_editor_quick_actions))
            device.findObjects(By.text(testContext.getString(R.string.compose_editor_pin_field))).last().click()
            clickText(testContext.getString(CommonString.multimedia_editor_image_field_editing_galery))
            device.findObjects(By.desc(testContext.getString(CommonString.save))).last().click()
            waitUntil(message = { "Quick actions did not save for Back" }) {
                scenario
                    .editorState()
                    .fields[1]
                    .toolbar.quickActions == listOf("pin", "gallery")
            }
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
            val gallery = testContext.getString(CommonString.multimedia_editor_image_field_editing_galery)
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
                remove("composeEditorToolbarPresets")
                remove("composeEditorSharedToolbarsMigrated")
                remove("note_editor_custom_buttons")
            }.commit()
        withEditor { scenario ->
            var web = scenario.awaitEditor()
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val bold = By.desc(testContext.getString(R.string.compose_editor_bold))
            var buttonLabel = "Wrap"
            // Keep the draggable selection on screen; this test checks field scoping,
            // while the toolbar list tests cover scrolling and the complete button set.
            val current = scenario.editorState()
            val model = scenario.editorModel()
            runBlocking {
                withContext(Dispatchers.Main) {
                    model.setFieldToolbar(
                        EditorFieldTarget(current.sessionId, current.generation, 0),
                        EditorFieldToolbar(
                            hiddenButtons = EditorToolbarButton.entries.map { it.name }.toSet() - setOf("BOLD", "ITALIC", "UNDERLINE"),
                        ),
                    )
                }
            }
            runBlocking { web.focusField(0) }
            checkNotNull(
                device.wait(Until.findObject(By.desc(testContext.getString(R.string.compose_editor_options))), 5_000),
            ).click()
            val toolbarEntry = By.text(testContext.getString(R.string.compose_editor_toolbar))
            checkNotNull(device.wait(Until.findObject(toolbarEntry), 5_000)).click()
            checkNotNull(
                device.wait(Until.findObject(By.text(testContext.getString(R.string.compose_editor_custom_buttons))), 5_000),
            ).click()
            checkNotNull(
                device.wait(Until.findObject(By.text(testContext.getString(com.ichi2.anki.CommonString.add_toolbar_item))), 5_000),
            ).click()
            assertTrue(device.wait(Until.hasObject(By.text(testContext.getString(R.string.compose_editor_custom_button))), 5_000))
            val inputs = device.findObjects(By.clazz("android.widget.EditText"))
            assertEquals(3, inputs.size)
            inputs[0].text = buttonLabel
            inputs[1].text = "<b>"
            inputs[2].text = "</b>"
            val save = By.text(testContext.getString(com.ichi2.anki.CommonString.save))
            device.findObjects(save).last().click()
            assertTrue(device.wait(Until.hasObject(By.text(buttonLabel)), 5_000))
            device.findObjects(save).last().click()
            waitUntil(message = { "The global template did not save" }) {
                scenario.editorState().customButtonTemplates.any { it.label == buttonLabel }
            }
            assertTrue(scenario.editorState().fields.all { it.toolbar.customButtons.isEmpty() })
            // Templates are already saved; dismiss the unchanged toolbar configuration.
            checkNotNull(
                device.wait(Until.findObject(By.desc(testContext.getString(com.ichi2.anki.CommonString.dialog_cancel))), 5_000),
            ).click()
            device.openEditorOptions()
            checkNotNull(device.wait(Until.findObject(toolbarEntry), 5_000)).click()
            checkNotNull(device.wait(Until.findObject(By.text(testContext.getString(R.string.compose_editor_bold))), 5_000)).click()
            // Manage shared definitions without committing the pending removal of Bold.
            checkNotNull(
                device.wait(Until.findObject(By.text(testContext.getString(R.string.compose_editor_custom_buttons))), 5_000),
            ).click()
            checkNotNull(device.wait(Until.findObject(By.text(buttonLabel)), 5_000)).click()
            buttonLabel = "Wrap updated"
            device.findObjects(By.clazz("android.widget.EditText"))[0].text = buttonLabel
            device.findObjects(save).last().click()
            device.findObjects(save).last().click()
            waitUntil(message = { "The template change did not reach the open toolbar configuration" }) {
                scenario.editorState().customButtonTemplates.any { it.label == buttonLabel }
            }
            assertTrue(
                "BOLD" !in
                    scenario
                        .editorState()
                        .fields[0]
                        .toolbar.hiddenButtons,
            )
            device.findObject(By.text(buttonLabel)).click()
            val handle =
                checkNotNull(
                    device.findObject(By.desc(testContext.getString(R.string.compose_editor_reorder_button, buttonLabel))),
                ).visibleCenter
            val destination = checkNotNull(device.findObject(By.text(testContext.getString(R.string.compose_editor_italic)))).visibleBounds
            assertTrue(device.swipe(handle.x, handle.y, handle.x, destination.bottom, 30))
            device.findObjects(By.desc(testContext.getString(CommonString.save))).last().click()
            waitUntil(message = { "The field toolbar did not save" }) {
                scenario
                    .editorState()
                    .fields[0]
                    .toolbar.customButtons
                    .any { it.label == buttonLabel }
            }
            // Saving returns to the preset list in the same dialog window.
            device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.text(buttonLabel)), 5_000))
            assertFalse(device.hasObject(bold))
            assertTrue(
                device.findObject(By.desc(testContext.getString(R.string.compose_editor_italic))).visibleBounds.right <=
                    device.findObject(By.text(buttonLabel)).visibleBounds.left,
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
            device.findObject(By.text(buttonLabel)).click()
            web.awaitJavascript("${richField(0)}.querySelector('b,strong')?.textContent === 'alpha'")
            runBlocking { web.focusField(1) }
            assertTrue(device.wait(Until.hasObject(bold), 5_000))
            assertFalse(device.hasObject(By.text(buttonLabel)))
            val configuredToolbar = scenario.editorState().fields[0].toolbar
            scenario.recreate()
            web = scenario.awaitEditor()
            assertEquals(configuredToolbar, scenario.editorState().fields[0].toolbar)
            // Mounted fields precede completion of draft restoration. Wait for native controls before focusing.
            assertTrue(device.wait(Until.hasObject(By.desc(testContext.getString(CommonString.save)).enabled(true)), 5_000))
            runBlocking { web.focusField(0) }
            assertTrue(device.wait(Until.hasObject(By.text(buttonLabel)), 5_000))
            assertFalse(device.hasObject(bold))
            assertTrue(
                device.findObject(By.desc(testContext.getString(R.string.compose_editor_italic))).visibleBounds.right <=
                    device.findObject(By.text(buttonLabel)).visibleBounds.left,
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
                        // A lazy item can enter semantics while its icon is still clipped
                        // by the toolbar edge. Scroll it fully into view before tapping.
                        val bounds = button.visibleBounds
                        if (bounds.width() >= bounds.height()) {
                            button.click()
                            return
                        }
                    }
                    val bounds = device.findObjects(By.scrollable(true)).maxBy { it.visibleBounds.bottom }.visibleBounds
                    device.swipe(
                        bounds.right - bounds.width() / 5,
                        bounds.centerY(),
                        bounds.left + bounds.width() / 5,
                        bounds.centerY(),
                        30,
                    )
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
    fun formattingDialogsRestoreTheKeyboardAndSelectionOnCancel() {
        withEditor { scenario ->
            val web = scenario.awaitEditor()
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            web.setField(1, "Before middle after.")
            val state = scenario.editorState()
            val model = scenario.editorModel()
            val buttons = listOf(EditorToolbarButton.TEXT_COLOR, EditorToolbarButton.EQUATIONS, EditorToolbarButton.ALIGNMENT)
            runBlocking {
                withContext(Dispatchers.Main) {
                    model.setFieldToolbar(
                        EditorFieldTarget(state.sessionId, state.generation, 1),
                        EditorFieldToolbar(buttonOrder = buttons.map { it.name }),
                    )
                }
            }
            for (button in buttons) {
                for (hadKeyboard in listOf(true, false)) {
                    device.waitForIdle()
                    waitUntil(message = { "$button ($hadKeyboard): the editor did not regain window focus" }) {
                        var focused = false
                        scenario.onActivity { focused = web.hasWindowFocus() }
                        focused
                    }
                    runBlocking { web.focusField(1) }
                    waitUntil(message = {
                        captureScreenshot("keyboard-open-failed-$button-$hadKeyboard", web)
                        "$button ($hadKeyboard): the field keyboard did not open"
                    }) { scenario.keyboardVisible() }
                    if (!hadKeyboard) {
                        device.pressBack()
                        waitUntil(message = { "The field keyboard did not close" }) { !scenario.keyboardVisible() }
                    }
                    device.waitForIdle()
                    web.evaluate(
                        """
                        (() => {
                            const field = ${richField(1)};
                            const text = field.firstChild;
                            field.getRootNode().getSelection().setBaseAndExtent(text, 7, text, 13);
                        })()
                        """.trimIndent(),
                    )
                    val target = assertNotNull(runBlocking { web.captureTarget() })
                    device.waitForIdle()
                    // The icon itself stays enabled in accessibility even when its button is disabled.
                    val buttonSelector = By.clickable(true).enabled(true).hasDescendant(By.desc(testContext.getString(button.label)))
                    val control = device.wait(Until.findObject(buttonSelector), 5_000)
                    if (control == null) captureScreenshot("toolbar-missing-$button-$hadKeyboard", web)
                    checkNotNull(control).click()
                    val dialog =
                        when (button) {
                            EditorToolbarButton.TEXT_COLOR -> By.res(testContext.packageName, "colorPickerView")
                            EditorToolbarButton.EQUATIONS -> By.text(testContext.getString(R.string.compose_editor_mathjax_equation))
                            else -> By.text(testContext.getString(R.string.compose_editor_alignment))
                        }
                    val opened = device.wait(Until.findObject(dialog), 5_000)
                    if (opened == null) captureScreenshot("dialog-missing-$button-$hadKeyboard", web)
                    assertNotNull(opened, "$button ($hadKeyboard): the formatting dialog did not open")
                    if (button == EditorToolbarButton.EQUATIONS) {
                        // Also restore a closed field keyboard after editing in the dialog's own input.
                        val formula = checkNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5_000))
                        formula.click()
                        formula.text = "x^2"
                    }
                    val cancel = device.wait(Until.findObject(By.text(testContext.getString(CommonString.dialog_cancel))), 5_000)
                    if (cancel == null) captureScreenshot("cancel-missing-$button-$hadKeyboard", web)
                    assertNotNull(cancel, "$button ($hadKeyboard): the dialog's Cancel button was not visible").click()
                    assertTrue(device.wait(Until.gone(dialog), 5_000))
                    waitUntil(message = { "$button did not restore keyboard visibility to $hadKeyboard" }) {
                        scenario.keyboardVisible() == hadKeyboard
                    }
                    assertEquals(target, runBlocking { web.captureTarget() }, "$button moved the cancelled selection")
                    assertEquals("Before middle after.", runBlocking { web.snapshot().fields[1] })
                }
            }
        }
    }

    @Test
    fun nativeColourAndEquationDialogsKeepTheirCapturedDestination() {
        Prefs.sharedPrefs
            .edit()
            .putString("composeEditorTextColor", "#1976d2")
            .putString("composeEditorHighlightColor", "#ffc107")
            .commit()
        withEditor { scenario ->
            val web = scenario.awaitEditor()
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            web.setField(0, "alpha.")
            web.setField(1, "beta")

            fun selectFront() {
                runBlocking { web.focusField(0) }
                waitUntil(message = { "The field keyboard did not open" }) { scenario.keyboardVisible() }
                device.waitForIdle()
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
                val target = By.clickable(true).enabled(true).hasDescendant(By.desc(testContext.getString(label)))
                repeat(5) {
                    device.wait(Until.findObject(target), 1_000)?.let { button ->
                        button.click()
                        return
                    }
                    val bounds = device.findObjects(By.scrollable(true)).maxBy { it.visibleBounds.bottom }.visibleBounds
                    device.swipe(
                        bounds.right - bounds.width() / 5,
                        bounds.centerY(),
                        bounds.left + bounds.width() / 5,
                        bounds.centerY(),
                        30,
                    )
                    device.waitForIdle()
                }
                error("Toolbar item not found: ${testContext.getString(label)}")
            }
            selectFront()
            toolbar(R.string.compose_editor_text_color)
            checkNotNull(device.wait(Until.findObject(By.res(testContext.packageName, "colorPickerView")), 5_000))
            captureScreenshot("format-colour", web)
            runBlocking { web.focusField(1) }
            // End Gboard's resumed composition; focus movement must not redirect the saved range.
            web.evaluate("${richField(1)}.blur()")
            clickText(testContext.getString(CommonString.dialog_ok))
            captureScreenshot("format-colour-applied", web)
            val applied = web.evaluate("${richField(0)}.innerHTML")
            assertTrue(
                applied.contains("#1976d2", ignoreCase = true) || applied.contains("rgb(25, 118, 210)"),
                "Selected colour was not applied: $applied",
            )
            web.awaitJavascript("getComputedStyle(${richField(0)}.querySelector('font,span')).color === 'rgb(25, 118, 210)'")
            assertEquals("beta", runBlocking { web.snapshot().fields[1] })
            assertEquals("#1976d2", scenario.editorState().toolbarTextColor)
            assertEquals("#ffc107", scenario.editorState().toolbarHighlightColor)
            waitUntil(message = { "Applying the colour did not restore the field keyboard" }) { scenario.keyboardVisible() }

            web.setField(0, "alpha.")
            selectFront()
            device.pressBack()
            waitUntil(message = { "The field keyboard did not close" }) { !scenario.keyboardVisible() }
            toolbar(R.string.compose_editor_equations)
            assertTrue(device.wait(Until.hasObject(By.text(testContext.getString(R.string.compose_editor_mathjax_equation))), 5_000))
            val formula = checkNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5_000))
            formula.click()
            formula.text = "x^2"
            captureScreenshot("format-equation", web)
            clickText(testContext.getString(CommonString.dialog_ok))
            web.awaitJavascript("${richField(0)}.querySelector('anki-mathjax')?.dataset.mathjax === 'x^2'")
            assertEquals(listOf("\\(x^2\\)", "beta"), runBlocking { web.snapshot().fields })
            waitUntil(message = { "Applying the equation did not restore the closed keyboard" }) { !scenario.keyboardVisible() }
            // A tap on the rendered formula reopens the same native editor.
            web.evaluate("${richField(0)}.querySelector('anki-mathjax').click()")
            assertTrue(device.wait(Until.hasObject(By.text(testContext.getString(R.string.compose_editor_mathjax_equation))), 5_000))
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
            web.awaitJavascript(
                "[...document.querySelectorAll('.plain-text-badge, .deferred-source')].every(button => getComputedStyle(button).display === 'none')",
            )
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
            clickSave()
            waitUntil(timeout = 30.seconds, message = { "Large note Save did not close the editor" }) {
                scenario.state == Lifecycle.State.DESTROYED
            }
            assertEquals(expected, runBlocking { withCol { getNote(note.id).fields.toList() } })
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.R)
    fun firstKeyboardOpeningDoesNotMoveToolbarDownAtAnimationEnd() {
        data class Frame(
            val launch: Int,
            val time: Long,
            val imeBottom: Int,
            val fieldAreaBottom: Int,
        )

        val frames = CopyOnWriteArrayList<Frame>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val current = AtomicReference<ComposeNoteEditorActivity>()
        val removeListeners = mutableListOf<() -> Unit>()
        val minimumKeyboardHeight = (100 * testContext.resources.displayMetrics.density).toInt()
        var launches = 0
        val callback =
            ActivityLifecycleCallback { activity, stage ->
                if (activity is ComposeNoteEditorActivity && stage == Stage.RESUMED && current.get() !== activity) {
                    current.set(activity)
                    val launch = launches++
                    val root = activity.window.decorView
                    var imeBottom = ViewCompat.getRootWindowInsets(root)?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
                    root.setWindowInsetsAnimationCallback(
                        object : WindowInsetsAnimation.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                            override fun onProgress(
                                insets: WindowInsets,
                                runningAnimations: MutableList<WindowInsetsAnimation>,
                            ): WindowInsets {
                                imeBottom = insets.getInsets(WindowInsets.Type.ime()).bottom
                                return insets
                            }

                            override fun onEnd(animation: WindowInsetsAnimation) {
                                imeBottom = ViewCompat.getRootWindowInsets(root)?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
                            }
                        },
                    )
                    val listener =
                        ViewTreeObserver.OnPreDrawListener {
                            val web = root.webViews().filterIsInstance<WebEditorView>().firstOrNull()
                            if (web != null) {
                                val location = IntArray(2)
                                web.getLocationOnScreen(location)
                                // Once the keyboard is open the footer has a fixed height, so its
                                // movement is also the movement of the field area's bottom edge.
                                frames.add(Frame(launch, SystemClock.uptimeMillis(), imeBottom, location[1] + web.height))
                            }
                            true
                        }
                    root.viewTreeObserver.addOnPreDrawListener(listener)
                    removeListeners.add {
                        if (root.viewTreeObserver.isAlive) root.viewTreeObserver.removeOnPreDrawListener(listener)
                        root.setWindowInsetsAnimationCallback(null)
                    }
                }
            }
        val originalEnabled = Prefs.isComposeNoteEditorEnabled
        val originalLayout = Prefs.sharedPrefs.getString("composeEditorToolbarDisplay", null)
        Prefs.isComposeNoteEditorEnabled = true
        Prefs.sharedPrefs.edit { putString("composeEditorToolbarDisplay", EditorToolbarDisplay.SCROLL.name) }
        instrumentation.runOnMainSync { ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(callback) }
        try {
            // Direct ActivityScenario editor launches miss the race between Add navigation,
            // Chromium's input connection and the first keyboard request. Repeat the real path.
            ActivityScenario.launch<DeckPicker>(Intent(testContext, DeckPicker::class.java)).use {
                repeat(12) {
                    val previous = current.get()
                    checkNotNull(device.wait(Until.findObject(By.res(testContext.packageName, "fab_main")), 5_000)).click()
                    checkNotNull(device.wait(Until.findObject(By.text(testContext.getString(CommonString.menu_add))), 5_000)).click()
                    waitUntil(message = { "The first on-screen keyboard did not open" }) {
                        var visible = false
                        instrumentation.runOnMainSync {
                            val editor = current.get()
                            visible = editor != null && editor !== previous &&
                                (
                                    ViewCompat
                                        .getRootWindowInsets(
                                            editor.window.decorView,
                                        )?.getInsets(WindowInsetsCompat.Type.ime())
                                        ?.bottom
                                        ?: 0
                                ) >
                                minimumKeyboardHeight
                        }
                        visible
                    }
                    device.waitForIdle()
                    SystemClock.sleep(350) // Include the layout following the final animation frame.
                    device.pressBack()
                    device.waitForIdle()
                    device.pressBack()
                    checkNotNull(device.wait(Until.findObject(By.res(testContext.packageName, "fab_main")), 5_000))
                }
            }
        } finally {
            instrumentation.runOnMainSync {
                ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(callback)
                removeListeners.forEach { it() }
            }
            Prefs.isComposeNoteEditorEnabled = originalEnabled
            Prefs.sharedPrefs.edit { putString("composeEditorToolbarDisplay", originalLayout) }
            File(testContext.getExternalFilesDir(null), "keyboard-layout.csv").writeText(
                "launch,time,imeBottom,fieldAreaBottom\n" +
                    frames.joinToString("\n") {
                        "${it.launch},${it.time},${it.imeBottom},${it.fieldAreaBottom}"
                    },
            )
        }
        val byLaunch = frames.groupBy { it.launch }
        assertEquals(12, byLaunch.size)
        for ((launch, samples) in byLaunch) {
            val nearEnd = samples.maxOf { it.imeBottom } * 0.8
            val openingFrames =
                samples.zipWithNext().filter { (before, after) ->
                    before.imeBottom >= nearEnd && after.imeBottom >= before.imeBottom && after.time - before.time < 100
                }
            assertTrue(openingFrames.isNotEmpty(), "No keyboard animation frames captured for launch $launch")
            for ((before, after) in openingFrames) {
                assertTrue(after.fieldAreaBottom - before.fieldAreaBottom <= 2, "Toolbar jumped down: $before -> $after")
            }
        }
    }

    @Test
    fun mainMenuPreservesKeyboardAndCaret() {
        withEditor { scenario ->
            val web = scenario.awaitEditor()
            web.setField(1, "Keep the caret here")
            runBlocking { web.focusField(1) }
            val minimumKeyboardHeight = (100 * testContext.resources.displayMetrics.density).toInt()
            waitUntil(message = { "A visible on-screen keyboard is required for this regression" }) {
                scenario.keyboardVisible() && scenario.keyboardHeight() > minimumKeyboardHeight
            }
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val overflow =
                By
                    .desc(
                        testContext.getString(R.string.compose_editor_options),
                    ).enabled(true)
            val menu = By.text(testContext.getString(R.string.compose_editor_field_options))
            val caret = assertNotNull(runBlocking { web.captureTarget() })
            checkNotNull(device.wait(Until.findObject(overflow), 5_000)).click()
            assertTrue(device.wait(Until.hasObject(menu), 5_000))
            device.waitForIdle()
            captureScreenshot("main-menu-keyboard", web)
            assertTrue(scenario.keyboardVisible(), "Opening the menu must leave the keyboard open")
            assertTrue(scenario.keyboardHeight() > minimumKeyboardHeight, "The on-screen keyboard must remain visible")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                // Before the platform overlay Back API, the IME consumes the first Back itself.
                device.pressBack()
                waitUntil(message = { "The keyboard did not close" }) { !scenario.keyboardVisible() }
            }
            device.pressBack()
            assertTrue(device.wait(Until.gone(menu), 5_000), "Back must dismiss the menu first")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                assertTrue(scenario.keyboardVisible(), "Dismissing the menu must leave the keyboard open")
            }
            assertEquals(caret.bookmark, runBlocking { web.captureTarget() }?.bookmark)

            runBlocking { web.focusField(1) }
            waitUntil(message = { "The keyboard did not reopen" }) { scenario.keyboardHeight() > minimumKeyboardHeight }
            device.openFieldOptions()
            val sheet = By.descStartsWith(testContext.getString(R.string.compose_editor_field_options_title, "Back"))
            assertTrue(device.wait(Until.hasObject(sheet), 5_000))
            assertTrue(scenario.keyboardVisible(), "Opening field options from the menu must keep the keyboard open")
            assertEquals(caret.bookmark, runBlocking { web.captureTarget() }?.bookmark)
            assertEquals("Keep the caret here", runBlocking { web.snapshot().fields[1] })
        }
    }

    @Test
    fun mainMenuStaysAnchoredWithAndWithoutOnScreenKeyboard() {
        withEditor { scenario ->
            val web = scenario.awaitEditor()
            runBlocking { web.focusField(1) }
            val density = testContext.resources.displayMetrics.density
            waitUntil(message = { "A visible on-screen keyboard is required for this regression" }) {
                scenario.keyboardHeight() > 100 * density
            }
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val overflow = By.desc(testContext.getString(R.string.compose_editor_options))
            val menu = By.text(testContext.getString(R.string.compose_editor_field_options))
            for (keyboardVisible in listOf(true, false)) {
                if (!keyboardVisible) {
                    scenario.onActivity {
                        WindowCompat.getInsetsController(it.window, it.window.decorView).hide(WindowInsetsCompat.Type.ime())
                    }
                    waitUntil(message = { "The keyboard did not close" }) { !scenario.keyboardVisible() }
                }
                device.waitForIdle()
                val button = checkNotNull(device.wait(Until.findObject(overflow), 5_000))
                val anchor = button.parent.visibleBounds
                button.click()
                val item = checkNotNull(device.wait(Until.findObject(menu), 5_000))
                captureScreenshot("main-menu-anchored-keyboard-$keyboardVisible", web)
                val bounds = item.parent.parent.visibleBounds
                val gap = bounds.top - anchor.bottom
                assertTrue(gap >= -1 && gap <= 8 * density, "The menu must sit below its button; gap was ${gap / density}dp")
                assertTrue(anchor.centerX() in bounds.left..bounds.right, "The menu must align horizontally with its button")
                assertEquals(keyboardVisible, scenario.keyboardVisible())
                // Dismiss outside the menu without touching a field or changing its focus.
                device.click(anchor.left / 2, anchor.centerY())
                assertTrue(device.wait(Until.gone(menu), 5_000))
            }
        }
    }

    @Test
    fun tappingCurrentOrDifferentFieldClosesOptions() {
        withEditor { scenario ->
            val web = scenario.awaitEditor()
            runBlocking { web.focusField(1) }
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val bold = By.desc(testContext.getString(R.string.compose_editor_bold))
            for (index in listOf(1, 0)) {
                device.openFieldOptions()
                assertTrue(device.wait(Until.hasObject(By.text("Pin field")), 5_000))
                // Restoring focus for a native control must not count as a field tap.
                runBlocking { web.focusField(1) }
                device.waitForIdle()
                assertFalse(device.hasObject(bold))

                // On smaller screens the keyboard and sheet cover Back. Expose the fields
                // before calculating a physical tap, while leaving the options open.
                scenario.onActivity {
                    WindowCompat.getInsetsController(it.window, it.window.decorView).hide(WindowInsetsCompat.Type.ime())
                }
                waitUntil(message = { "The keyboard still covers the target field" }) {
                    var hidden = false
                    scenario.onActivity {
                        hidden = ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == false
                    }
                    hidden
                }
                // The expanded settings now cover both inputs below the metadata header.
                // Use the editor's scrolled-header state to expose them for a real field tap.
                scenario.onActivity { web.onHeaderVisibility?.invoke(false) }
                assertTrue(device.wait(Until.gone(By.text(testContext.getString(R.string.compose_editor_deck))), 5_000))
                device.waitForIdle()
                assertFalse(device.hasObject(bold), "Hiding the keyboard must leave field options open")

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
                val tapY = location[1] + (point.getDouble("y") * point.getDouble("scale")).toInt()
                val sheetTop =
                    checkNotNull(
                        device.findObject(By.descStartsWith(testContext.getString(R.string.compose_editor_field_options_title, "Back"))),
                    ).visibleBounds.top
                assertTrue(tapY < sheetTop, "The target input must be visible above the sheet")
                assertTrue(
                    device.click(
                        location[0] + (point.getDouble("x") * point.getDouble("scale")).toInt(),
                        tapY,
                    ),
                )
                assertTrue(device.wait(Until.hasObject(bold), 5_000), "Tapping field $index must restore formatting controls")
                assertEquals(index, runBlocking { web.captureTarget() }?.field)
            }
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 29)
    fun fieldOptionsHideNativeCursorHandlesWithoutChangingTheCaretOrKeyboard() {
        withEditor { scenario ->
            val web = scenario.awaitEditor()
            // Put the caret near the footer so the sheet covers it on taller screens too.
            web.setField(1, (1..40).joinToString("<br>") { "Keep insertion point $it" })
            val original = runBlocking { web.snapshot().fields }
            runBlocking { web.focusField(1) }
            waitUntil(message = { "The keyboard did not open" }) { scenario.keyboardVisible() }
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val bounds = JSONObject(web.evaluate("${richField(1)}.getBoundingClientRect().toJSON()"))
            val ratio = web.evaluate("devicePixelRatio").toDouble()
            val tapY = minOf(bounds.getDouble("bottom") - 12, web.evaluate("innerHeight").toDouble() - 24)
            val location = IntArray(2)
            scenario.onActivity { web.getLocationOnScreen(location) }
            device.click(
                location[0] + ((bounds.getDouble("x") + 30) * ratio).toInt(),
                location[1] + (tapY * ratio).toInt(),
            )

            fun visibleHandles(): Int {
                var count = 0

                fun visit(view: View) {
                    if (view.javaClass.simpleName == "PopupTouchHandleDrawable" && view.isShown) count++
                    if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
                }
                scenario.onActivity {
                    android.view.inspector.WindowInspector
                        .getGlobalWindowViews()
                        .forEach(::visit)
                }
                return count
            }
            waitUntil(message = { "A real tap must expose Chromium's native insertion handle" }) { visibleHandles() > 0 }
            val target = assertNotNull(runBlocking { web.captureTarget() })
            device.openFieldOptions()
            device.waitForIdle()
            waitUntil(message = { "Native cursor handles must not draw over Field options" }) { visibleHandles() == 0 }
            captureScreenshot("field-options-native-cursor", web)
            assertTrue(scenario.keyboardVisible(), "Keep the keyboard connected while options are open")
            assertEquals(target, runBlocking { web.captureTarget() })
            checkNotNull(device.findObject(By.desc(testContext.getString(R.string.compose_editor_close_field_options)))).click()
            device.waitForIdle()
            assertTrue(scenario.keyboardVisible())
            assertEquals(target, runBlocking { web.captureTarget() }, "Closing options must preserve the insertion point")
            web.awaitJavascript("document.documentElement.style.clipPath === ''")
            assertEquals(original, runBlocking { web.snapshot().fields })
        }
    }

    @Test
    fun outsideTapClosesFieldOptionsWithoutChangingFocusOrKeyboard() {
        withEditor { scenario ->
            val web = scenario.awaitEditor()
            web.setField(1, "Keep the caret here")
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val heading = By.descStartsWith(testContext.getString(R.string.compose_editor_field_options_title, "Back"))
            val pin = By.desc(testContext.getString(R.string.compose_editor_pin_field))
            val density = testContext.resources.displayMetrics.density

            fun keyboardVisible(): Boolean {
                var visible = false
                scenario.onActivity {
                    visible = ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
                }
                return visible
            }
            for (showKeyboard in listOf(true, false)) {
                runBlocking { web.focusField(1) }
                waitUntil(message = { "The keyboard did not open" }) { keyboardVisible() }
                if (!showKeyboard) {
                    scenario.onActivity {
                        WindowCompat.getInsetsController(it.window, it.window.decorView).hide(WindowInsetsCompat.Type.ime())
                    }
                    waitUntil(message = { "The keyboard did not hide" }) { !keyboardVisible() }
                }
                val target = assertNotNull(runBlocking { web.captureTarget() })
                device.openFieldOptions()
                assertNotNull(device.wait(Until.findObject(heading), 5_000))
                device.waitForIdle()

                // The gap between the pin and keyboard controls belongs to the sheet.
                val pinBounds = checkNotNull(device.findObject(pin)).visibleBounds
                device.click(device.displayWidth / 2, pinBounds.centerY())
                assertTrue(device.hasObject(heading), "Tapping sheet padding must leave it open")
                assertEquals(showKeyboard, keyboardVisible())

                // The outer margin above the sheet is blank in both the native metadata
                // and WebView, depending on the available height with the IME open/closed.
                val titleBounds = checkNotNull(device.findObject(heading)).visibleBounds
                device.click((2 * density).toInt(), titleBounds.top - (24 * density).toInt())
                assertTrue(device.wait(Until.gone(heading), 5_000), "Tapping outside must dismiss field options")
                device.waitForIdle()
                assertEquals(showKeyboard, keyboardVisible(), "Outside dismissal must preserve the keyboard state")
                val restored = assertNotNull(runBlocking { web.captureTarget() })
                assertEquals(target.field, restored.field)
                assertEquals(target.bookmark, restored.bookmark, "Outside dismissal must preserve the caret")
            }
        }
    }

    @Test
    fun fieldOptionsRemainReachableWhenKeyboardReopens() {
        withEditor { scenario ->
            val web = scenario.awaitEditor()
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val density = testContext.resources.displayMetrics.density
            runBlocking { web.focusField(1) }
            waitUntil(message = { "The on-screen keyboard did not open" }) { scenario.keyboardHeight() > 100 * density }
            device.openFieldOptions()
            val title = By.descStartsWith(testContext.getString(R.string.compose_editor_field_options_title, "Back"))
            assertNotNull(device.wait(Until.findObject(title), 5_000))
            val lastOption = By.text(testContext.getString(R.string.compose_editor_field_description))

            for ((index, visible) in listOf(true, false, true, false, true).withIndex()) {
                scenario.onActivity {
                    val controller = WindowCompat.getInsetsController(it.window, it.window.decorView)
                    if (visible) controller.show(WindowInsetsCompat.Type.ime()) else controller.hide(WindowInsetsCompat.Type.ime())
                }
                waitUntil(message = { "The keyboard did not reach visible=$visible" }) {
                    if (visible) scenario.keyboardHeight() > 100 * density else !scenario.keyboardVisible()
                }
                device.waitForIdle()
                val keyboardTop = device.displayHeight - scenario.keyboardHeight()
                captureScreenshot("field-options-keyboard-before-scroll-$index-$visible", web)
                // Target the actual scroll container: display coordinates can start inside
                // the gesture-navigation area when the keyboard is hidden.
                val sheet =
                    checkNotNull(device.wait(Until.findObject(By.pkg(testContext.packageName).clazz("android.widget.ScrollView")), 5_000))
                repeat(2) { sheet.scroll(Direction.DOWN, 0.8f) }
                captureScreenshot("field-options-keyboard-$index-$visible", web)
                val last =
                    checkNotNull(
                        device.wait(Until.findObject(lastOption), 5_000),
                    ) { "Last option missing at iteration $index, IME=$visible" }
                assertTrue(last.visibleBounds.height() > 16 * density, "The last setting must be readable")
                assertTrue(last.visibleBounds.bottom <= keyboardTop, "The last setting must be above the keyboard")
                assertEquals(visible, scenario.keyboardVisible(), "Scrolling options must not dismiss the keyboard")
            }
        }
    }

    @Test
    fun fieldOptionsDisappearAfterDismissingKeyboardAndPressingBack() {
        withEditor { scenario ->
            val web = scenario.awaitEditor()
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val options = By.desc(testContext.getString(R.string.compose_editor_options)).enabled(true)
            // Initial focus happens after fields mount; wait for the native controls before changing it.
            assertNotNull(device.wait(Until.findObject(options), 5_000))
            runBlocking { web.focusField(1) }

            fun keyboardVisible(): Boolean {
                var visible = false
                scenario.onActivity {
                    visible = ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
                }
                return visible
            }
            waitUntil(message = { "The keyboard did not open for the field" }) { keyboardVisible() }
            device.openFieldOptions()
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
            val options = By.desc(testContext.getString(R.string.compose_editor_options)).enabled(true)
            val backOptions = By.descStartsWith(testContext.getString(R.string.compose_editor_field_options_title, "Back"))

            fun toggleOptions() {
                if (device.hasObject(backOptions)) {
                    checkNotNull(device.findObject(By.desc(testContext.getString(R.string.compose_editor_close_field_options)))).click()
                } else {
                    device.openFieldOptions()
                }
            }

            fun choose(label: Int) =
                checkNotNull(device.wait(Until.findObject(By.descContains(testContext.getString(label)).enabled(true)), 5_000)).click()

            assertNotNull(device.wait(Until.findObject(options), 5_000))
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
            val pin = By.checkable(true).desc(testContext.getString(R.string.compose_editor_pin_field))
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
            clickSave()
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

            clickSave()
            waitUntil(timeout = 30.seconds, message = { "Add did not save to the chosen deck" }) {
                runBlocking { withCol { noteCount() == 1 } }
            }
            assertEquals(destination, runBlocking { withCol { getNote(findNotes("").single()).firstCard(this).did } })
        }
    }

    @Test
    fun emptySaveFeedbackStaysAboveToolbarWhileKeyboardOpens() {
        withEditor { scenario ->
            val web = scenario.awaitEditor()
            assertEquals(listOf("", ""), runBlocking { web.snapshot().fields })
            scenario.onActivity {
                WindowCompat.getInsetsController(it.window, web).hide(WindowInsetsCompat.Type.ime())
            }
            waitUntil(message = { "The keyboard did not close" }) {
                !checkNotNull(ViewCompat.getRootWindowInsets(web)).isVisible(WindowInsetsCompat.Type.ime())
            }
            val bottomMargins = CopyOnWriteArrayList<Int>()
            val shownMessage = AtomicReference<String>()
            val frames =
                ViewTreeObserver.OnPreDrawListener {
                    val feedback = web.rootView.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
                    feedback?.let { shownMessage.set(it.text.toString()) }
                    var parent = feedback?.parent
                    while (parent is View && parent !is Snackbar.SnackbarLayout) parent = parent.parent
                    (parent as? Snackbar.SnackbarLayout)?.let {
                        bottomMargins.add((it.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin)
                    }
                    true
                }
            scenario.onActivity {
                it.window.decorView.viewTreeObserver
                    .addOnPreDrawListener(frames)
            }
            try {
                clickSave()
                waitUntil(message = { "The empty-field error did not appear" }) { bottomMargins.isNotEmpty() }
                waitUntil(message = { "The error should focus the first field and open its keyboard" }) {
                    checkNotNull(ViewCompat.getRootWindowInsets(web)).isVisible(WindowInsetsCompat.Type.ime())
                }
                assertEquals(0, runBlocking { withCol { noteCount() } }, "The first empty save must not add a note")
                assertEquals(TR.addingTheFirstFieldIsEmpty(), shownMessage.get())
                assertEquals(
                    1,
                    bottomMargins.distinct().size,
                    "Compose already handles the keyboard inset; the native snackbar margin must remain constant: $bottomMargins",
                )
                captureScreenshot("empty-save-keyboard-feedback", web)
            } finally {
                scenario.onActivity {
                    it.window.decorView.viewTreeObserver
                        .removeOnPreDrawListener(frames)
                }
            }
        }
    }

    @Test
    fun emptyFirstFieldIsBrieflyHighlightedAfterSave() {
        assertEmptyFirstFieldHighlight(sourceMode = false)
    }

    @Test
    fun emptyFirstFieldIsBrieflyHighlightedInHtmlMode() {
        assertEmptyFirstFieldHighlight(sourceMode = true)
    }

    private fun assertEmptyFirstFieldHighlight(sourceMode: Boolean) {
        withEditor { scenario ->
            val web = scenario.awaitEditor()
            web.setField(1, "Keep this back")
            if (sourceMode) {
                runBlocking {
                    web.focusField(0)
                    assertTrue(web.execute(WebEditorAction.SOURCE_MODE))
                }
            }
            runBlocking { web.focusField(1) }
            val first = "document.querySelector('.field-container[data-index=\"0\"] .editor-field')"
            val animation = "$first?.getAnimations().find(animation => animation.id === 'field-error')"
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val save = checkNotNull(device.findObject(By.desc(testContext.getString(CommonString.save)))).visibleBounds
            // Accessibility synchronization can outlast this brief cue. Hold it at creation,
            // then resume it below to verify that the production animation expires naturally.
            web.evaluate(
                """
                (() => {
                    const field = $first;
                    const animate = field.animate.bind(field);
                    field.animate = (...args) => {
                        const animation = animate(...args);
                        animation.pause();
                        animation.currentTime = 0;
                        return animation;
                    };
                })();
                """.trimIndent(),
            )
            device.click(save.centerX(), save.centerY())
            web.awaitJavascript("!!($animation)")
            waitUntil(message = { "The empty-field error did not appear" }) {
                var shown = false
                scenario.onActivity {
                    shown = it.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.text ==
                        TR.addingTheFirstFieldIsEmpty()
                }
                shown
            }
            val mode = if (sourceMode) "source" else "rich"
            captureScreenshot("empty-first-field-$mode", web)
            assertEquals(
                "true",
                web.evaluate(
                    """
                    (() => {
                        const field = $first;
                        const bounds = field.getBoundingClientRect();
                        const style = getComputedStyle(field);
                        const color = style.outlineColor.match(/\d+/g).map(Number);
                        return field.matches(':focus-within') && bounds.top >= 0 && bounds.bottom <= innerHeight
                            && style.outlineStyle === 'solid' && parseFloat(style.outlineWidth) >= 1.5
                            && color[0] > color[1] && color[0] > color[2];
                    })()
                    """.trimIndent(),
                ),
                "The first field should be visible, focused and outlined in red (allowing for pixel rounding)",
            )
            assertEquals(
                "0",
                web.evaluate("document.querySelector('.field-container[data-index=\"1\"] .editor-field').getAnimations().length"),
            )
            assertEquals(listOf("", "Keep this back"), runBlocking { web.snapshot().fields })
            assertEquals(0, runBlocking { withCol { noteCount() } })
            val errorColor = web.evaluate("getComputedStyle($first).outlineColor")
            web.evaluate("($animation).play()")
            web.awaitJavascript("!($animation)")
            assertFalse(errorColor == web.evaluate("getComputedStyle($first).outlineColor"), "The error outline must clear automatically")
            // A retry must show the cue again, and correcting the note must still permit Save.
            clickSave()
            web.awaitJavascript("!!($animation)")
            runBlocking { assertTrue(web.execute(WebEditorAction.INSERT_TEXT, value = "Now filled")) }
            clickSave()
            waitUntil(message = { "Correcting the first field did not allow Save" }) { runBlocking { withCol { noteCount() == 1 } } }
            assertEquals(listOf("Now filled", "Keep this back"), runBlocking { withCol { getNote(findNotes("").single()).fields } })
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

            clickSave()

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

            clickSave()

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
            val saveBounds = device.findObject(UiSelector().description(testContext.getString(CommonString.save))).bounds
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
            // Tap the control's position to verify the disabled button ignores input.
            // Avoid waiting for an idle busy spinner.
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
    fun freshAddDoesNotRestoreAnAbandonedCheckpoint() = assertDraftReopening(keepForNextLaunch = false)

    @Test
    fun explicitlyKeptDraftRestoresItsCompleteStateAndSavingEndsRetention() = assertDraftReopening(keepForNextLaunch = true)

    private fun assertDraftReopening(keepForNextLaunch: Boolean) {
        val deckId = col.decks.id("Recovered draft deck")
        lateinit var previousModel: EditorViewModel
        lateinit var session: String
        lateinit var initial: EditorState
        withEditor { scenario ->
            val web = scenario.awaitEditor()
            previousModel = scenario.editorModel()
            initial = scenario.editorState()
            web.setField(0, "Recovered front 日本")
            web.setField(1, "<b>Recovered back</b>")
            runBlocking {
                withContext(Dispatchers.Main) {
                    previousModel.selectDeck(deckId)
                    previousModel.setTags(listOf("recovered-tag", "日本語"))
                }
                web.updateHostState(withContext(Dispatchers.Main) { previousModel.hostState() })
                previousModel.rememberDraft(keepForNextLaunch)
            }
            session = scenario.editorState().sessionId
            // Closing the scenario finishes the host without taking its explicit discard path.
        }

        withEditor { scenario ->
            val web = scenario.awaitEditor()
            assertNotSame(previousModel, scenario.editorModel())
            if (keepForNextLaunch) {
                assertEquals(session, scenario.editorState().sessionId)
                assertEquals(listOf("Recovered front 日本", "<b>Recovered back</b>"), runBlocking { web.snapshot().fields })
                assertTrue(runBlocking { web.snapshot().hasChanges })
                assertEquals(deckId, scenario.editorState().deckId)
                assertEquals(listOf("recovered-tag", "日本語"), scenario.editorState().tags)
                assertTrue(scenario.editorState().hasMetadataChanges)
            } else {
                assertNotEquals(session, scenario.editorState().sessionId)
                assertEquals(listOf("", ""), runBlocking { web.snapshot().fields })
                assertFalse(runBlocking { web.snapshot().hasChanges })
                assertEquals(initial.deckId, scenario.editorState().deckId)
                assertEquals(initial.tags, scenario.editorState().tags)
                assertFalse(scenario.editorState().hasMetadataChanges)
            }
            assertEquals(0, runBlocking { withCol { noteCount() } })
            if (keepForNextLaunch) {
                clickSave()
                waitUntil(message = { "The recovered draft was not saved and cleared" }) {
                    runBlocking { withCol { noteCount() } } == 1 && runBlocking { web.snapshot().fields } == listOf("", "")
                }
            }
        }
        if (keepForNextLaunch) {
            withEditor { scenario ->
                val web = scenario.awaitEditor()
                assertNotEquals(session, scenario.editorState().sessionId)
                assertEquals(listOf("", ""), runBlocking { web.snapshot().fields })
                assertEquals(1, runBlocking { withCol { noteCount() } })
            }
        }
    }

    @Test
    fun imageOcclusionDefaultDoesNotRedirectComposeAddToLegacy() {
        val ioType =
            runBlocking {
                withCol {
                    config.addingDefaultsMode = AddingDefaultsMode.DECIDE_BY_NOTE_TYPE
                    addImageOcclusionNoteType()
                    notetypes.setCurrent(notetypes.all().first { it.isImageOcclusion })
                    defaultsForAdding().notetypeId
                }
            }
        withEditor { scenario ->
            val web = scenario.awaitEditor()
            assertTrue(scenario.editorState().isAdding)
            assertFalse(scenario.editorState().noteTypeId == ioType)
            web.setField(0, "Compose remains available")
            assertEquals("Compose remains available", runBlocking { web.snapshot().fields.first() })
            assertEquals(ioType, runBlocking { withCol { defaultsForAdding().notetypeId } })
            assertEquals(0, runBlocking { withCol { noteCount() } })
        }
    }

    @Test
    fun imageOcclusionPicksAnImageAndPreservesTheComposeDraft() = assertImageOcclusionSelection(existingType = true)

    @Test
    fun imageOcclusionCanBeSelectedBeforeItsNoteTypeExists() = assertImageOcclusionSelection(existingType = false)

    @Test
    fun imageOcclusionOffersClipboardAndRestoresTheChoiceAfterRecreation() =
        assertImageOcclusionSelection(existingType = false, source = OcclusionSource.CLIPBOARD)

    @Test
    fun imageOcclusionCanUseGalleryWhenAnImageIsOnTheClipboard() =
        assertImageOcclusionSelection(existingType = true, source = OcclusionSource.GALLERY_WITH_CLIPBOARD)

    private enum class OcclusionSource { GALLERY, CLIPBOARD, GALLERY_WITH_CLIPBOARD }

    private fun assertImageOcclusionSelection(
        existingType: Boolean,
        source: OcclusionSource = OcclusionSource.GALLERY,
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val image = File(testContext.cacheDir, "picker-source/occlusion-test.png").also { it.parentFile!!.mkdirs() }
        val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
        image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val selectedImage = AtomicReference<Uri?>()
        val picks = AtomicInteger()
        val launched = AtomicReference<ImageOcclusionArgs.Add?>()
        val monitor =
            object : Instrumentation.ActivityMonitor() {
                override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                    if (intent.action == Intent.ACTION_GET_CONTENT) {
                        assertEquals("image/*", intent.type)
                        picks.incrementAndGet()
                        return selectedImage.get()?.let { Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(it)) }
                            ?: Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
                    }
                    if (intent.getStringExtra(SingleFragmentActivity.EXTRA_FRAGMENT_NAME) == ImageOcclusion::class.java.name) {
                        launched.set(
                            BundleCompat.getParcelable(
                                checkNotNull(intent.getBundleExtra(SingleFragmentActivity.EXTRA_FRAGMENT_ARGS)),
                                ImageOcclusionViewModel.ARG_IMAGE_OCCLUSION,
                                ImageOcclusionArgs.Add::class.java,
                            ),
                        )
                    }
                    return null
                }
            }
        instrumentation.addMonitor(monitor)
        try {
            val ioType =
                runBlocking {
                    withCol {
                        if (existingType) {
                            addImageOcclusionNoteType()
                            notetypes.all().first { it.isImageOcclusion }.let { EditorChoice(it.id, it.name) }
                        } else {
                            notetypes.all().filter { it.isImageOcclusion }.forEach { notetypes.remove(it.id) }
                            assertFalse(notetypes.all().any { it.isImageOcclusion })
                            EditorChoice(0, TR.notetypesImageOcclusionName())
                        }
                    }
                }
            withEditor { scenario ->
                var web = scenario.awaitEditor()
                // Seed an unfocused draft so Gboard does not start composing the synthetic input.
                web.evaluate("${richField(0)}.blur()")
                scenario.onActivity {
                    web.clearFocus()
                    WindowCompat.getInsetsController(it.window, web).hide(WindowInsetsCompat.Type.ime())
                    it.getSystemService<ClipboardManager>()!!.setPrimaryClip(ClipData.newPlainText("test", "text only"))
                }
                web.setField(0, "Keep this unfinished question")
                web.setField(1, "Keep this answer")
                val state = scenario.editorState()
                val device = UiDevice.getInstance(instrumentation)
                clickText(state.noteTypeName)
                clickText(ioType.name)
                waitUntil(message = { "The image picker did not open" }) { picks.get() == 1 }
                assertEquals(null, launched.get())
                assertEquals(state.noteTypeId, scenario.editorState().noteTypeId)

                selectedImage.set(FileProvider.getUriForFile(testContext, "${testContext.packageName}.apkgfileprovider", image))
                if (source != OcclusionSource.GALLERY) {
                    scenario.onActivity {
                        it.getSystemService<ClipboardManager>()!!.setPrimaryClip(
                            ClipData.newUri(it.contentResolver, "Image Occlusion test", selectedImage.get()),
                        )
                    }
                }
                clickText(state.noteTypeName)
                clickText(ioType.name)
                if (source != OcclusionSource.GALLERY) {
                    val paste = By.text(testContext.getString(CommonString.paste_from_clipboard))
                    assertTrue(device.wait(Until.hasObject(paste), 5_000), "Offer the clipboard image before opening Gallery")
                    assertEquals(1, picks.get())
                    captureScreenshot("image-occlusion-source", web)
                    // Dismissing the choice must leave the editor usable for another attempt.
                    device.pressBack()
                    assertEquals(null, launched.get())
                    clickText(state.noteTypeName)
                    clickText(ioType.name)
                    checkNotNull(device.wait(Until.findObject(paste), 5_000))
                    if (source == OcclusionSource.CLIPBOARD) {
                        scenario.recreate()
                        web = scenario.awaitEditor()
                        checkNotNull(device.wait(Until.findObject(paste), 5_000))
                    }
                    clickText(
                        testContext.getString(
                            if (source ==
                                OcclusionSource.CLIPBOARD
                            ) {
                                CommonString.paste_from_clipboard
                            } else {
                                CommonString.multimedia_editor_image_field_editing_galery
                            },
                        ),
                    )
                }
                waitUntil(timeout = 30.seconds, message = { "The mask editor did not open" }) { launched.get() != null }
                assertEquals(if (source == OcclusionSource.CLIPBOARD) 1 else 2, picks.get())
                val args = checkNotNull(launched.get())
                assertEquals(ioType.id, args.noteTypeId)
                assertEquals(state.deckId, args.originalDeckId)
                assertTrue(image.readBytes().contentEquals(File(args.imagePath).readBytes()))
                var maskEditor: SingleFragmentActivity? = null
                waitUntil(timeout = 30.seconds, message = { "The mask editor did not resume" }) {
                    maskEditor = TestUtils.activityInstance as? SingleFragmentActivity
                    maskEditor != null
                }
                val activity = checkNotNull(maskEditor)
                var maskWeb: WebView? = null
                waitUntil(message = { "The mask WebView did not appear" }) {
                    instrumentation.runOnMainSync {
                        maskWeb =
                            activity.window.decorView
                                .webViews()
                                .firstOrNull()
                    }
                    maskWeb != null
                }
                checkNotNull(maskWeb).awaitJavascript("globalThis.maskEditor != null")
                captureScreenshot("image-occlusion-from-compose-${if (existingType) "existing" else "new"}", checkNotNull(maskWeb))
                instrumentation.runOnMainSync { activity.finish() }
                assertTrue(web === scenario.awaitEditor())
                assertEquals(listOf("Keep this unfinished question", "Keep this answer"), runBlocking { web.snapshot().fields })
                assertEquals(state.noteTypeId, scenario.editorState().noteTypeId)
                assertEquals(0, runBlocking { withCol { noteCount() } })
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            image.delete()
        }
    }

    @Test
    fun phonePreviewRendersTheDraftAndReturnsToTheSameEditor() {
        withEditor { scenario ->
            scenario.onActivity { assumeTrue(it.resources.configuration.smallestScreenWidthDp < 600) }
            val editor = scenario.awaitEditor()
            scenario.onActivity { activity ->
                assertFalse(activity.supportFragmentManager.fragments.any { it is EditorPreviewFragment })
            }
            editor.setField(0, "Phone unsaved question")
            editor.setField(1, "Phone unsaved answer")
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            checkNotNull(
                device.wait(Until.findObject(By.desc(testContext.getString(CommonString.card_editor_preview_card)).enabled(true)), 10_000),
            ).click()
            var cardWebView: WebView? = null
            waitUntil(timeout = 30.seconds, message = { "Phone preview did not appear" }) {
                scenario.onActivity { activity ->
                    cardWebView =
                        activity.supportFragmentManager.fragments
                            .filterIsInstance<EditorPreviewFragment>()
                            .firstOrNull()
                            ?.view
                            ?.webViews()
                            ?.firstOrNull()
                }
                cardWebView != null
            }
            val preview = checkNotNull(cardWebView)
            preview.awaitJavascript("document.getElementById('qa')?.textContent.includes('Phone unsaved question') === true")
            clickText(testContext.getString(CommonString.show_answer))
            preview.awaitJavascript("document.getElementById('qa')?.textContent.includes('Phone unsaved answer') === true")
            captureScreenshot("phone-preview-answer", editor, preview)
            assertEquals(0, runBlocking { withCol { noteCount() } })
            device.pressBack()
            waitUntil(message = { "Phone preview did not close" }) {
                var closed = false
                scenario.onActivity { activity -> closed = activity.supportFragmentManager.fragments.none { it is EditorPreviewFragment } }
                closed
            }
            assertTrue(editor === scenario.awaitEditor())
            assertEquals(listOf("Phone unsaved question", "Phone unsaved answer"), runBlocking { editor.snapshot().fields })
            captureScreenshot("phone-preview-dismissed", editor)
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

    private fun UiDevice.openEditorOptions() {
        val overflow = By.desc(testContext.getString(R.string.compose_editor_options)).enabled(true)
        checkNotNull(wait(Until.findObject(overflow), 5_000)).click()
    }

    private fun UiDevice.openFieldOptions() {
        openEditorOptions()
        checkNotNull(wait(Until.findObject(By.text(testContext.getString(R.string.compose_editor_field_options))), 5_000)).click()
    }

    private fun ActivityScenario<ComposeNoteEditorActivity>.keyboardHeight(): Int {
        var height = 0
        onActivity {
            height = ViewCompat.getRootWindowInsets(it.window.decorView)?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
        }
        return height
    }

    private fun ActivityScenario<ComposeNoteEditorActivity>.keyboardVisible(): Boolean {
        var visible = false
        onActivity {
            visible = ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        return visible
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

    private fun clickSave() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        checkNotNull(device.wait(Until.findObject(By.desc(testContext.getString(CommonString.save)).enabled(true)), 10_000)).click()
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
