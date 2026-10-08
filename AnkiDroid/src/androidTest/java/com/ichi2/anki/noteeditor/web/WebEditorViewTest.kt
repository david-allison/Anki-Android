// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.web

import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.test.core.app.ActivityScenario
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.GrantStoragePermission.storagePermission
import com.ichi2.anki.testutil.TestInputMethodRule
import com.ichi2.anki.testutil.awaitJavascript
import com.ichi2.anki.testutil.ensureWebViewIsSupported
import com.ichi2.anki.testutil.evaluate
import com.ichi2.anki.testutil.grantPermissions
import com.ichi2.anki.testutil.notificationPermission
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Runs the production assets, virtual origin, native bridge and Chromium editing engine. */
class WebEditorViewTest : InstrumentedTest() {
    @get:Rule
    val runtimePermissionRule = grantPermissions(storagePermission, notificationPermission)

    @get:Rule
    val keyboard = TestInputMethodRule()

    @Test
    fun untouchedHtmlRoundTripsAndSnapshotIncludesTheLatestDom() =
        withEditor {
            val html = "<b>été&nbsp;</b><img src='a b.png'><br/>"
            loadDocument(document(html))
            assertEquals(html, snapshot().fields.first())
            assertFalse(snapshot().hasChanges)
            // A snapshot must not depend on the input event or a debounce timer.
            evaluate("${fieldElement(0)}.textContent = 'latest 日本'")
            assertEquals("latest 日本", snapshot().fields.first())
            assertTrue(snapshot().hasChanges)
        }

    @Test
    fun delayedInsertionKeepsItsOriginalFieldAndRejectsEditedOrReplacedTargets() =
        withEditor {
            loadDocument(document("front"))
            select(0, 1, 3)
            val target = assertNotNull(captureTarget())
            select(1, 0, 4)
            assertTrue(execute(WebEditorAction.INSERT_TEXT, target, "X"))
            assertEquals(listOf("fXnt", "back"), snapshot().fields)
            assertFalse(execute(WebEditorAction.INSERT_TEXT, target, "stale"))
            select(0, 0, 0)
            val oldSession = assertNotNull(captureTarget())
            loadDocument(document("replacement").copy(generation = 1))
            assertFalse(execute(WebEditorAction.INSERT_TEXT, oldSession, "wrong note"))
            assertEquals("replacement", snapshot().fields.first())
        }

    @Test
    fun noteTypeChangesKeepTheBaselineAndSavingCanDisableInput() =
        withEditor {
            loadDocument(document("clean"))
            evaluate("${fieldElement(0)}.textContent = 'changed'")
            val current = snapshot().fields.first()
            loadDocument(document(current).copy(generation = 1), resetBaseline = false)
            assertTrue(snapshot().hasChanges)
            select(0, 0, 0)
            setInputEnabled(false)
            assertEquals("changed", snapshot().fields.first())
            assertFalse(execute(WebEditorAction.INSERT_TEXT, value = "during save"))
            assertEquals("false", evaluate("${fieldElement(0)}.isContentEditable"))
            loadDocument(document(""))
            setInputEnabled(true)
            assertFalse(snapshot().hasChanges)
            assertEquals("true", evaluate("${fieldElement(0)}.isContentEditable"))
        }

    @Test
    fun draftRestoresFieldsNativeStateBaselineAndPendingTargetTogether() =
        withEditor {
            val draftId = "instrumentation-${UUID.randomUUID()}"
            try {
                loadDocument(document("clean"))
                select(0, 5, 5)
                assertTrue(execute(WebEditorAction.INSERT_TEXT, value = " changed"))
                val target = assertNotNull(captureTarget())
                createDraft(draftId, "{\"deck\":1,\"tags\":[\"old\"]}")
                updateHostState("{\"deck\":2,\"tags\":[\"new\"]}")
                loadDocument(document("different"))
                val restored = assertNotNull(restoreDraft(draftId))
                assertEquals(listOf("clean", "back"), restored.baseline)
                assertEquals("{\"deck\":2,\"tags\":[\"new\"]}", restored.hostStateJson)
                assertEquals("clean changed", snapshot().fields.first())
                assertTrue(snapshot().hasChanges)
                assertTrue(execute(WebEditorAction.INSERT_TEXT, target, " media"))
                assertEquals("clean changed media", snapshot().fields.first())
            } finally {
                discardDraft(draftId)
            }
        }

    @Test
    fun caretMovementDoesNotRewriteDraftButLatestInputIsRecovered() =
        withEditor {
            val draftId = "instrumentation-${UUID.randomUUID()}"
            try {
                loadDocument(document("front"))
                createDraft(draftId, "{}")
                evaluate(
                    """
                    window.draftPuts = 0;
                    const originalPut = IDBObjectStore.prototype.put;
                    IDBObjectStore.prototype.put = function(...args) {
                        window.draftPuts++;
                        return originalPut.apply(this, args);
                    };
                    """.trimIndent(),
                )
                select(0, 1, 1)
                select(1, 2, 2)
                evaluate("document.dispatchEvent(new Event('selectionchange'))")
                snapshot()
                assertEquals("0", evaluate("window.draftPuts"))

                evaluate(
                    """
                    window.blockDraftRefresh = event => event.stopImmediatePropagation();
                    document.addEventListener('anki-editor-fields-change', window.blockDraftRefresh, true);
                    document.addEventListener('selectionchange', window.blockDraftRefresh, true);
                    ${fieldElement(0)}.textContent = 'latest input';
                    """.trimIndent(),
                )
                // Reading first must not consume the change needed by the background checkpoint.
                assertEquals("latest input", snapshot().fields.first())
                assertEquals("0", evaluate("window.draftPuts"))
                evaluate(
                    """
                    document.removeEventListener('anki-editor-fields-change', window.blockDraftRefresh, true);
                    document.removeEventListener('selectionchange', window.blockDraftRefresh, true);
                    document.dispatchEvent(new Event('selectionchange'));
                    """.trimIndent(),
                )
                awaitJavascript("window.draftPuts > 0")
                loadDocument(document("temporary"))
                val restored = assertNotNull(restoreDraft(draftId))
                assertEquals(
                    "latest input",
                    restored.document.fields
                        .first()
                        .html,
                )
                assertEquals(listOf("front", "back"), restored.baseline)
            } finally {
                discardDraft(draftId)
            }
        }

    @Test
    fun noteHtmlCannotExecuteScriptsInTheBridgeDocument() =
        withEditor {
            loadDocument(document("<img src='missing' onerror='window.noteCodeExecuted=true'>"))
            awaitJavascript("${fieldElement(0)}.querySelector('img').complete")
            assertEquals("false", evaluate("window.noteCodeExecuted === true"))
            assertEquals("\"${WebEditorView.ORIGIN}\"", evaluate("location.origin"))
        }

    @Test
    fun formattedJapaneseCompositionIsNotDuplicatedAndSnapshotsDoNotBlur() =
        withEditor {
            loadDocument(document(""))
            focusField()
            keyboard.awaitEditor()
            assertTrue(execute(WebEditorAction.BOLD))
            keyboard.perform("compose", "に")
            keyboard.perform("compose", "日本")
            awaitJavascript("${fieldElement(0)}.textContent === '日本'")
            val composingHtml = snapshot().fields.first()
            assertTrue(composingHtml.contains("日本"))
            assertEquals("true", evaluate("document.activeElement.shadowRoot.activeElement.id === 'field-0'"))
            assertFalse(execute(WebEditorAction.ITALIC))
            // A view change can finish composition; formatting must remain guarded above.
            assertTrue(execute(WebEditorAction.SOURCE_MODE))
            awaitJavascript(
                """
                (() => {
                    const source = document.querySelector('.editor-field[data-field-ordinal="0"] .CodeMirror');
                    return source?.getBoundingClientRect().height > 0 && source.CodeMirror.getValue().includes('日本');
                })()
                """.trimIndent(),
            )
            keyboard.perform("finish")
            awaitJavascript("${fieldElement(0)}.textContent === '日本'")
            assertEquals("\"日本\"", evaluate("${fieldElement(0)}.textContent"))
            assertEquals(composingHtml, snapshot().fields.first())
            assertTrue(snapshot().fields.first().let { it.contains("<b>日本</b>") || it.contains("<strong>日本</strong>") })
        }

    @Test
    fun sourceModeUsesAnkiCodeMirrorAndDoesNotExposeCollectionRpc() =
        withEditor {
            loadDocument(document("front"))
            select(0, 0, 5)
            assertTrue(execute(WebEditorAction.SOURCE_MODE))
            awaitJavascript("document.querySelector('.editor-field[data-field-ordinal=\"0\"] .CodeMirror') !== null")
            evaluate(
                "document.querySelector('.editor-field[data-field-ordinal=\"0\"] .CodeMirror').CodeMirror.setValue('<b>source</b>')",
            )
            assertEquals("<b>source</b>", snapshot().fields.first())
            assertEquals("true", evaluate("document.querySelector('.fields-only') !== null"))
            evaluate("fetch('/_anki/addNote', {method: 'POST', body: new Uint8Array()}).catch(() => window.collectionRpcDenied = true)")
            awaitJavascript("window.collectionRpcDenied === true")
        }

    private fun document(front: String) =
        WebEditorDocument("test-session", 0, listOf(WebEditorField("Front", front), WebEditorField("Back", "back")))

    private suspend fun WebEditorView.select(
        field: Int,
        start: Int,
        end: Int,
    ) {
        focusField(field)
        evaluate(
            """
            (() => {
                const field = ${fieldElement(field)};
                const range = document.createRange();
                range.setStart(field.firstChild, $start);
                range.setEnd(field.firstChild, $end);
                const selection = field.getRootNode().getSelection();
                selection.removeAllRanges();
                selection.addRange(range);
            })();
            """.trimIndent(),
        )
    }

    private fun fieldElement(index: Int) =
        "document.querySelector('.editor-field[data-field-ordinal=\"$index\"] .rich-text-editable').shadowRoot.querySelector('anki-editable')"

    private fun withEditor(block: suspend WebEditorView.() -> Unit) {
        ensureWebViewIsSupported()
        val intent = SingleFragmentActivity.getIntent(testContext, Fragment::class)
        ActivityScenario.launch<SingleFragmentActivity>(intent).use { scenario ->
            lateinit var webView: WebEditorView
            scenario.onActivity { activity ->
                webView = WebEditorView(activity, activity.cacheDir)
                activity.setContentView(webView)
            }
            try {
                runBlocking { webView.block() }
            } finally {
                scenario.onActivity {
                    (webView.parent as? ViewGroup)?.removeView(webView)
                    webView.destroy()
                }
            }
        }
    }
}
