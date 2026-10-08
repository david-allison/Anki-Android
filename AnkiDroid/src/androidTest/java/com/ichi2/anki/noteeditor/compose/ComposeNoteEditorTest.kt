// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CommonString
import com.ichi2.anki.NoteEditorFragment
import com.ichi2.anki.NoteEditorFragment.Companion.NoteEditorCaller
import com.ichi2.anki.R
import com.ichi2.anki.noteeditor.web.WebEditorView
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
            clickDescription(testContext.getString(R.string.compose_editor_source))
            web.awaitJavascript(
                """
                (() => {
                    const source = document.querySelector('.editor-field[data-field-ordinal="0"] .CodeMirror');
                    return source?.offsetHeight > 0 && source.CodeMirror.getValue() === '<b>Retained front</b>';
                })()
                """.trimIndent(),
            )
            captureScreenshot("phone-source", web)

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
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val saveText = testContext.getString(CommonString.save)
            waitUntil(timeout = 5.seconds, message = { "Save remained enabled while Back awaited its snapshot" }) {
                !device.findObject(UiSelector().text(saveText).enabled(true)).exists()
            }
            // A tap at the disabled Save control must not start a collection operation.
            device.findObject(UiSelector().text(saveText)).click()
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
            preview.awaitJavascript("document.getElementById('qa')?.textContent.includes('Preview unsaved front') === true")
            preview.awaitJavascript("getComputedStyle(document.getElementById('qa')).opacity === '1'")
            captureScreenshot("tablet-preview", editor, preview)
            clickText(testContext.getString(CommonString.show_answer))
            preview.awaitJavascript("document.getElementById('qa')?.textContent.includes('Preview first answer') === true")

            editor.setField(1, "Preview updated answer")

            preview.awaitJavascript("document.getElementById('qa')?.textContent.includes('Preview updated answer') === true")
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
        assertTrue(device.takeScreenshot(File(directory, "$name.png")))
    }

    private fun clickText(text: String) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val button = device.findObject(UiSelector().text(text).enabled(true))
        waitUntil(timeout = 30.seconds, message = { "Enabled button not found: $text" }) { button.exists() }
        assertTrue(button.click(), "Unable to click: $text")
    }

    private fun clickDescription(description: String) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val button = device.findObject(UiSelector().description(description).enabled(true))
        waitUntil(timeout = 30.seconds, message = { "Enabled button not found: $description" }) { button.exists() }
        assertTrue(button.click(), "Unable to click: $description")
    }

    private fun View.webViews(): List<WebView> =
        when (this) {
            is WebView -> listOf(this)
            is ViewGroup -> (0 until childCount).flatMap { getChildAt(it).webViews() }
            else -> emptyList()
        }
}
