// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.web

import android.annotation.SuppressLint
import android.net.Uri
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.compose.ui.graphics.toArgb
import androidx.fragment.app.Fragment
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.uiautomator.UiDevice
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.common.utils.android.getColorFromAttr
import com.ichi2.anki.noteeditor.compose.EditorQuickAction
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.GrantStoragePermission.storagePermission
import com.ichi2.anki.testutil.TestInputMethodRule
import com.ichi2.anki.testutil.awaitJavascript
import com.ichi2.anki.testutil.ensureWebViewIsSupported
import com.ichi2.anki.testutil.evaluate
import com.ichi2.anki.testutil.grantPermissions
import com.ichi2.anki.testutil.notificationPermission
import com.ichi2.compose.theme.toMaterial3ColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Runs the production assets, virtual origin, native bridge and Chromium editing engine. */
class WebEditorViewTest : InstrumentedTest() {
    @get:Rule
    val runtimePermissionRule = grantPermissions(storagePermission, notificationPermission)

    @get:Rule
    val keyboard = TestInputMethodRule()

    @Test
    fun outerBackgroundAndLabelsFollowLightTheme() = assertOuterTheme(R.style.Theme_Light)

    @Test
    fun outerBackgroundAndLabelsFollowDarkTheme() = assertOuterTheme(R.style.Theme_Dark)

    @Test
    fun outerBackgroundAndLabelsFollowBlackTheme() = assertOuterTheme(R.style.Theme_Dark_Black)

    @Test
    fun quickPinIsCompactAndMatchesLightToolbar() = assertQuickPinAppearance(R.style.Theme_Light)

    @Test
    fun quickPinIsCompactAndMatchesDarkToolbar() = assertQuickPinAppearance(R.style.Theme_Dark)

    @Test
    fun quickPinIsCompactAndMatchesBlackToolbar() = assertQuickPinAppearance(R.style.Theme_Dark_Black)

    @Test
    fun quickActionsShowPressFeedbackInLightTheme() = assertQuickActionPressFeedback(R.style.Theme_Light)

    @Test
    fun quickActionsShowPressFeedbackInDarkTheme() = assertQuickActionPressFeedback(R.style.Theme_Dark)

    private fun assertQuickActionPressFeedback(theme: Int) =
        withEditor(theme = theme) {
            val pin = EditorQuickAction.PIN.toWeb(context, pinned = true)
            loadDocument(document("front").let { it.copy(fields = it.fields.map { field -> field.copy(quickActions = listOf(pin)) }) })
            select(0, 1, 3)
            val actions = LinkedBlockingQueue<String>()
            getInstrumentation().runOnMainSync { onQuickAction = { action, _ -> actions.add(action) } }
            captureRenderedScreenshot("editor-quick-action-ready-$theme")
            val rect =
                JSONObject(
                    evaluate(
                        """
                        (() => {
                            const rect = document.querySelectorAll('.quick-action')[1].getBoundingClientRect();
                            return {x: (rect.left + rect.width / 2) * devicePixelRatio,
                                    y: (rect.top + rect.height / 2) * devicePixelRatio};
                        })()
                        """.trimIndent(),
                    ),
                )
            val location = IntArray(2)
            getInstrumentation().runOnMainSync { getLocationOnScreen(location) }
            val x = location[0] + rect.getDouble("x").toFloat()
            val y = location[1] + rect.getDouble("y").toFloat()
            val opacity = "Number(getComputedStyle(document.querySelectorAll('.quick-action')[1], '::after').opacity)"

            fun touch(
                action: Int,
                downTime: Long,
            ) {
                val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                try {
                    assertTrue(getInstrumentation().uiAutomation.injectInputEvent(event, true))
                } finally {
                    event.recycle()
                }
            }

            val downTime = SystemClock.uptimeMillis()
            touch(MotionEvent.ACTION_DOWN, downTime)
            try {
                awaitJavascript("$opacity > 0.1")
                SystemClock.sleep(ViewConfiguration.getLongPressTimeout().toLong() + 100)
                assertEquals("true", evaluate("$opacity > 0.1"), "Keep feedback visible throughout a long press")
                captureRenderedScreenshot("editor-quick-action-held-$theme")
                assertNull(actions.poll(), "Pressing must not activate before release")
                assertEquals("true", evaluate("${fieldElement(0)}.matches(':focus')"), "Quick actions must not steal field focus")
            } finally {
                touch(MotionEvent.ACTION_CANCEL, downTime)
            }
            awaitJavascript("$opacity === 0")
            assertNull(actions.poll(), "A cancelled press must not activate")
            UiDevice.getInstance(getInstrumentation()).click(x.toInt(), y.toInt())
            assertEquals(EditorQuickAction.PIN.id, actions.poll(5, TimeUnit.SECONDS), "A normal tap must still activate")
            awaitJavascript("$opacity === 0")
            assertEquals("front", snapshot().fields.first())
        }

    @Test
    fun fieldWidthsStayStableWhenTheScrollbarAppearsAndDisappears() =
        withEditor {
            loadDocument(document("Short field"))
            awaitJavascript("document.querySelector('.scroll-area').scrollHeight <= document.querySelector('.scroll-area').clientHeight")
            val bounds =
                """
                (() => {
                    const field = document.querySelector('.editor-field').getBoundingClientRect();
                    const action = document.querySelector('.label-container').getBoundingClientRect();
                    return [field.left, field.right, action.left, action.right];
                })()
                """.trimIndent()
            val before = evaluate(bounds)
            evaluate("${fieldElement(0)}.innerHTML = '<div>Long field content</div>'.repeat(100)")
            awaitJavascript("document.querySelector('.scroll-area').scrollHeight > document.querySelector('.scroll-area').clientHeight")
            assertEquals(before, evaluate(bounds), "A scrollbar must not shift or narrow the fields and their controls")
            captureRenderedScreenshot("editor-stable-scrollbar")
            evaluate("${fieldElement(0)}.innerHTML = 'Short field'")
            awaitJavascript("document.querySelector('.scroll-area').scrollHeight <= document.querySelector('.scroll-area').clientHeight")
            assertEquals(before, evaluate(bounds), "Removing overflow must preserve the same alignment")
        }

    @Test
    fun fieldScrollbarIsSlimAndStillScrolls() =
        withEditor {
            loadDocument(document("<div>Long field content</div>".repeat(100)))
            awaitJavascript("document.querySelector('.scroll-area').scrollHeight > document.querySelector('.scroll-area').clientHeight")
            val width =
                evaluate("(() => { const area = document.querySelector('.scroll-area'); return area.offsetWidth - area.clientWidth; })()")
            assertEquals(4.0, width.toDouble(), 0.5, "Keep the field scrollbar to 4dp")
            evaluate("document.querySelector('.scroll-area').scrollTop = 200")
            awaitJavascript("document.querySelector('.scroll-area').scrollTop >= 199")
            captureRenderedScreenshot("editor-slim-scrollbar")
        }

    private fun assertQuickPinAppearance(theme: Int) =
        withEditor(theme = theme) {
            val pin = EditorQuickAction.PIN.toWeb(context, pinned = true)
            loadDocument(
                document("").let { it.copy(fields = it.fields.map { field -> field.copy(quickActions = listOf(pin)) }) },
            )
            val appearance =
                JSONObject(
                    evaluate(
                        """
                        (() => {
                            const button = document.querySelector('.quick-action');
                            const rect = button.getBoundingClientRect();
                            const circle = getComputedStyle(button, '::before');
                            const svg = button.querySelector('svg').getBoundingClientRect();
                            return {width: rect.width, height: rect.height, icon: svg.width,
                                circleWidth: circle.width, circleHeight: circle.height, circleColor: circle.backgroundColor,
                                foreground: getComputedStyle(button).color, background: getComputedStyle(button).backgroundColor,
                                pressed: button.getAttribute('aria-pressed'), label: button.getAttribute('aria-label'),
                                outerAreaClickable: document.elementFromPoint(rect.left + 1, rect.top + rect.height / 2) === button};
                        })()
                        """.trimIndent(),
                    ),
                )
            assertTrue(appearance.getDouble("width") >= 48 && appearance.getDouble("height") >= 48, "Keep a 48dp touch target: $appearance")
            assertTrue(appearance.getBoolean("outerAreaClickable"), "The transparent outer area must remain clickable")
            assertEquals(20.0, appearance.getDouble("icon"), 0.1)
            assertEquals("32px", appearance.getString("circleWidth"))
            assertEquals("32px", appearance.getString("circleHeight"))
            val colors = context.toMaterial3ColorScheme()

            fun rgb(color: Int) =
                "rgb(${android.graphics.Color.red(color)}, ${android.graphics.Color.green(color)}, ${android.graphics.Color.blue(color)})"
            assertEquals(rgb(colors.primaryContainer.toArgb()), appearance.getString("circleColor"))
            assertEquals(rgb(colors.onPrimaryContainer.toArgb()), appearance.getString("foreground"))
            assertEquals("rgba(0, 0, 0, 0)", appearance.getString("background"))
            assertEquals("true", appearance.getString("pressed"))
            assertEquals("${pin.label}: Front", appearance.getString("label"))
            captureRenderedScreenshot("editor-pin-$theme")
        }

    private fun WebEditorView.captureRenderedScreenshot(name: String) {
        val rendered = CountDownLatch(1)
        getInstrumentation().runOnMainSync {
            postVisualStateCallback(
                0,
                object : WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) {
                        postOnAnimation { postOnAnimation { rendered.countDown() } }
                    }
                },
            )
        }
        assertTrue(rendered.await(30, TimeUnit.SECONDS), "WebView did not render the screenshot")
        UiDevice.getInstance(getInstrumentation()).apply {
            waitForIdle()
            assertTrue(takeScreenshot(File(context.filesDir, "$name.png")))
        }
    }

    private fun assertOuterTheme(theme: Int) =
        withEditor(theme = theme) {
            loadDocument(document("<span style='color: rgb(200, 30, 40)'>coloured text</span>"))
            val foreground = getColorFromAttr(context, com.google.android.material.R.attr.colorOnSurface)

            fun rgb(color: Int) =
                "rgb(${android.graphics.Color.red(color)}, ${android.graphics.Color.green(color)}, ${android.graphics.Color.blue(color)})"
            val background = getColorFromAttr(context, android.R.attr.colorBackground)
            val appearance =
                JSONObject(
                    evaluate(
                        """
                        (() => {
                            const style = selector => getComputedStyle(document.querySelector(selector));
                            return {
                                body: style('body').backgroundColor,
                                container: style('.field-container').backgroundColor,
                                label: style('.label-name').color,
                                badge: style('.label-container .badge').color,
                                labelBackground: style('.label-container').backgroundColor,
                                field: style('.rich-text-input').backgroundColor,
                                content: getComputedStyle(${fieldElement(0)}.querySelector('span')).color
                            };
                        })()
                        """.trimIndent(),
                    ),
                )
            assertEquals("rgba(0, 0, 0, 0)", appearance.getString("body"))
            assertEquals("rgba(0, 0, 0, 0)", appearance.getString("container"))
            assertEquals(rgb(foreground), appearance.getString("label"))
            assertEquals(rgb(foreground), appearance.getString("badge"))
            assertEquals(rgb(background), appearance.getString("labelBackground"))
            assertTrue(appearance.getString("field").startsWith("rgb("), "Field surface must remain opaque")
            assertEquals("rgb(200, 30, 40)", appearance.getString("content"))
            evaluate("requestAnimationFrame(() => requestAnimationFrame(() => window.themeFrameReady = true))")
            awaitJavascript("window.themeFrameReady === true")
            getInstrumentation().waitForIdleSync()
            UiDevice.getInstance(getInstrumentation()).apply {
                waitForIdle()
                takeScreenshot(File(context.filesDir, "editor-theme-$theme.png"))
            }
        }

    @Test
    fun unboundEditorLoadsPackagedShellAndWaitsForItsCollection() =
        withEditor(initiallyBound = false) {
            val media = File.createTempFile("editor-binding-", ".txt", context.cacheDir)
            val draftId = "instrumentation-${UUID.randomUUID()}"
            var draftCreated = false
            media.writeText("collection media")
            try {
                awaitJavascript("typeof window.AnkiEditor?.request === 'function'")
                evaluate(
                    """
                    fetch('/media/${media.name}', {cache: 'no-store'}).then(async response => {
                        window.unboundMedia = {status: response.status, body: await response.text()};
                    });
                    """.trimIndent(),
                )
                awaitJavascript("window.unboundMedia?.status === 404")
                assertEquals("\"\"", evaluate("window.unboundMedia.body"))
                assertEquals("true", evaluate("window.AnkiEditorFields === undefined"))
                getInstrumentation().runOnMainSync {
                    bindCollection(context.cacheDir)
                    bindCollection(File(context.cacheDir, "."))
                    assertFailsWith<IllegalStateException> { bindCollection(File(context.cacheDir, "another-collection")) }
                }
                loadDocumentAndCreateDraft(document("bound fields"), draftId, "{}")
                draftCreated = true
                evaluate(
                    """
                    fetch('/media/${media.name}', {cache: 'no-store'}).then(async response => {
                        window.boundMedia = {status: response.status, body: await response.text()};
                    });
                    """.trimIndent(),
                )
                awaitJavascript("window.boundMedia?.status === 200")
                assertEquals("\"collection media\"", evaluate("window.boundMedia.body"))
                loadDocument(document("temporary"))
                val restored = assertNotNull(restoreDraft(draftId))
                assertEquals(
                    "bound fields",
                    restored.document.fields
                        .first()
                        .html,
                )
                assertEquals(listOf("bound fields", "back"), snapshot().fields)
            } finally {
                media.delete()
                if (draftCreated) discardDraft(draftId)
            }
        }

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
    fun draftRecoveryWorksWithoutExplicitCommitSupport() =
        withEditor {
            val draftId = "instrumentation-${UUID.randomUUID()}"
            loadDocument(document("fallback"))
            evaluate("window.savedCommit = IDBTransaction.prototype.commit; IDBTransaction.prototype.commit = undefined;")
            try {
                createDraft(draftId, "{\"deck\":2}")
                loadDocument(document("temporary"))
                val restored = assertNotNull(restoreDraft(draftId))
                assertEquals(
                    "fallback",
                    restored.document.fields
                        .first()
                        .html,
                )
                assertEquals("{\"deck\":2}", restored.hostStateJson)
            } finally {
                discardDraft(draftId)
                evaluate("IDBTransaction.prototype.commit = window.savedCommit;")
            }
        }

    @Test
    fun abortedDraftWriteRejectsAndPreservesTheLastCommittedDraft() {
        val draftId = "instrumentation-${UUID.randomUUID()}"
        withEditor {
            loadDocument(document("committed"))
            createDraft(draftId, "{\"deck\":1}")
            loadDocument(document("must not replace committed draft"))
            evaluate(
                """
                window.originalDraftPut = IDBObjectStore.prototype.put;
                IDBObjectStore.prototype.put = function(value, key) {
                    const request = window.originalDraftPut.call(this, value, key);
                    // The put succeeds, then a duplicate add aborts the whole transaction.
                    this.add(value, key);
                    return request;
                };
                """.trimIndent(),
            )
            assertFailsWith<IllegalStateException> { createDraft(draftId, "{\"deck\":2}") }
            // Keep the failure installed through pagehide: restoring put here would
            // let the final checkpoint successfully retry the rejected write.
            // Destroying this WebView also destroys its patched JavaScript context.
        }
        // A fresh WebView must recover only the fully committed transaction.
        withEditor {
            try {
                val restored = assertNotNull(restoreDraft(draftId))
                assertEquals(
                    "committed",
                    restored.document.fields
                        .first()
                        .html,
                )
                assertEquals("{\"deck\":1}", restored.hostStateJson)
            } finally {
                discardDraft(draftId)
            }
        }
    }

    @Test
    fun loadingDraftKeepsMappedBaselineAndNativeStateTogether() =
        withEditor {
            val draftId = "instrumentation-${UUID.randomUUID()}"
            try {
                loadDocument(document("clean"))
                loadDocumentAndCreateDraft(
                    document("changed").copy(generation = 1),
                    draftId,
                    "{\"tags\":[\"draft\"]}",
                    resetBaseline = false,
                )
                loadDocument(document("temporary"))
                val restored = assertNotNull(restoreDraft(draftId))
                assertEquals(1, restored.document.generation)
                assertEquals(listOf("clean", "back"), restored.baseline)
                assertEquals("{\"tags\":[\"draft\"]}", restored.hostStateJson)
                assertEquals(listOf("changed", "back"), snapshot().fields)
                assertTrue(snapshot().hasChanges)
            } finally {
                discardDraft(draftId)
            }
        }

    @Test
    fun failedDraftStorageWaitsForRenderingBeforeReleasingQueuedCommands() =
        withEditor {
            loadDocument(document("before"))
            evaluate(
                """
                indexedDB.open = () => { throw new Error('Draft storage denied'); };
                window.unhandledStorageFailure = false;
                window.addEventListener('unhandledrejection', () => window.unhandledStorageFailure = true);
                const loadFields = AnkiEditorFields.load;
                AnkiEditorFields.load = async (...args) => {
                    await new Promise(resolve => window.releaseFieldLoad = resolve);
                    return loadFields(...args);
                };
                const request = AnkiEditor.request;
                AnkiEditor.request = (...args) => {
                    window.lastEditorMethod = args[1];
                    return request(...args);
                };
                """.trimIndent(),
            )
            coroutineScope {
                val loading =
                    async(Dispatchers.Default) {
                        runCatching { loadDocumentAndCreateDraft(document("after"), "storage-denied", "{}") }
                    }
                try {
                    awaitJavascript("typeof window.releaseFieldLoad === 'function'")
                    val queuedSnapshot = async(Dispatchers.Default) { snapshot() }
                    awaitJavascript("window.lastEditorMethod === 'snapshot'")
                    assertFalse(loading.isCompleted)
                    assertFalse(queuedSnapshot.isCompleted)
                    evaluate("window.releaseFieldLoad()")
                    assertEquals("Draft storage denied", loading.await().exceptionOrNull()?.message)
                    assertEquals(listOf("after", "back"), queuedSnapshot.await().fields)
                    assertEquals("false", evaluate("window.unhandledStorageFailure"))
                } finally {
                    evaluate("window.releaseFieldLoad?.()")
                }
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
    fun shellInsertionPreservesPackagedHtmlAndLaterHeadLiterals() {
        val marker =
            testContext.assets
                .open("backend/editor-fields.json")
                .bufferedReader()
                .use { JSONObject(it.readText()) }
        val entryPoint = marker.optString("entryPoint", "index.html")
        val page =
            testContext.assets
                .open("backend/sveltekit/$entryPoint")
                .bufferedReader()
                .use { it.readText() }
        val shell = "<script>window.headLiteral = '<head>';</script>"
        assertTrue(page.contains("<head>"))
        assertEquals(page.replaceFirst("<head>", "<head>$shell"), insertEditorShell(page, shell))
        assertEquals(
            "<head>$shell</head><body><template><head></template></body>",
            insertEditorShell("<head></head><body><template><head></template></body>", shell),
        )
        assertEquals("<html><body>no head</body></html>", insertEditorShell("<html><body>no head</body></html>", shell))
    }

    @Test
    @SuppressLint("SetJavaScriptEnabled")
    fun mixedCaseClosingTagsStayInsideTheTrustedInlineScript() {
        ensureWebViewIsSupported()
        val intent = SingleFragmentActivity.getIntent(testContext, Fragment::class)
        ActivityScenario.launch<SingleFragmentActivity>(intent).use { scenario ->
            lateinit var webView: WebView
            scenario.onActivity { activity ->
                // Test HTML parsing without the production client's navigation/CSP restrictions.
                webView = WebView(activity).apply { settings.javaScriptEnabled = true }
                activity.setContentView(webView)
            }
            val script =
                """
                window.scriptLiteral = '</ScRiPt><script>window.injected = true</script>';
                window.scriptFinished = true;
                """.trimIndent()

            fun loadScript(
                source: String,
                marker: String,
            ) {
                val html = "<html><head><script>$source</script></head><body id='$marker'></body></html>"
                scenario.onActivity { webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null) }
                webView.awaitJavascript("document.body?.id === '$marker'")
            }

            try {
                // A control proves this fixture would detect an unescaped closing tag.
                loadScript(script, "unescaped")
                assertEquals("true", webView.evaluate("window.injected === true"))
                loadScript(escapeInlineEditorScript(script), "escaped")
                assertEquals("true", webView.evaluate("window.scriptFinished === true"))
                assertEquals("true", webView.evaluate("window.scriptLiteral === '</script><script>window.injected = true</script>'"))
                assertEquals("true", webView.evaluate("window.injected === undefined"))
            } finally {
                scenario.onActivity {
                    (webView.parent as? ViewGroup)?.removeView(webView)
                    webView.destroy()
                }
            }
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
    @SdkSuppress(minSdkVersion = 26)
    fun localHostExceptionDoesNotOpenTheRequestBoundary() =
        withEditor {
            loadDocument(document("front"))
            getInstrumentation().runOnMainSync {
                assertTrue(settings.safeBrowsingEnabled)
                for (url in listOf(
                    "https://example.invalid/editor-fields",
                    "https://sub.appassets.androidplatform.net/editor-fields",
                    "http://appassets.androidplatform.net/editor-fields",
                    "${WebEditorView.ORIGIN}:443/editor-fields",
                )) {
                    val request =
                        object : WebResourceRequest {
                            override fun getUrl(): Uri = Uri.parse(url)

                            override fun isForMainFrame(): Boolean = false

                            override fun isRedirect(): Boolean = false

                            override fun hasGesture(): Boolean = false

                            override fun getMethod(): String = "GET"

                            override fun getRequestHeaders(): Map<String, String> = emptyMap()
                        }
                    assertEquals(404, webViewClient.shouldInterceptRequest(this, request)?.statusCode, url)
                }
            }
        }

    @Test
    fun modeQuickActionsFollowEachFieldsActualMode() =
        withEditor {
            val mode = EditorQuickAction.EDIT_HTML.toWeb(context, pinned = false)
            val fields =
                document("<b>front</b>").fields.mapIndexed { index, field ->
                    field.copy(sourceMode = index == 1, quickActions = listOf(mode))
                }
            loadDocument(document("").copy(fields = fields))
            val front = "document.querySelectorAll('.quick-action')[0]"
            val back = "document.querySelectorAll('.quick-action')[1]"
            awaitJavascript("$back?.getAttribute('aria-label') === 'Edit rich text: Back'")
            assertEquals("true", evaluate("$front.getAttribute('aria-checked') === 'true'"))
            assertEquals("false", evaluate("$back.getAttribute('aria-checked') === 'true'"))
            val width = evaluate("$front.getBoundingClientRect().width")
            focusField(0)
            assertTrue(execute(WebEditorAction.SOURCE_MODE))
            awaitJavascript("$front.getAttribute('aria-label') === 'Edit rich text: Front'")
            assertEquals(width, evaluate("$front.getBoundingClientRect().width"), "Switching must not move adjacent controls")
            updateFieldActions(fields.map { it.quickActions })
            assertEquals(
                "false",
                evaluate("$front.getAttribute('aria-checked') === 'true'"),
                "Configuration updates must preserve live mode",
            )
            assertTrue(execute(WebEditorAction.SOURCE_MODE))
            awaitJavascript("$front.getAttribute('aria-label') === 'Edit HTML: Front'")
            assertEquals("false", evaluate("$back.getAttribute('aria-checked') === 'true'"), "Other fields keep their own mode")
            assertEquals(listOf("<b>front</b>", "back"), snapshot().fields)
            captureRenderedScreenshot("editor-mode-switches")
            assertEquals(
                "true",
                evaluate("$front.getAttribute('aria-checked') === 'true'"),
                "Rich mode must remain selected after rendering",
            )
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

    private fun withEditor(
        initiallyBound: Boolean = true,
        theme: Int? = null,
        block: suspend WebEditorView.() -> Unit,
    ) {
        ensureWebViewIsSupported()
        val intent = SingleFragmentActivity.getIntent(testContext, Fragment::class)
        ActivityScenario.launch<SingleFragmentActivity>(intent).use { scenario ->
            lateinit var webView: WebEditorView
            scenario.onActivity { activity ->
                val editorContext = theme?.let { ContextThemeWrapper(activity, it) } ?: activity
                if (theme !=
                    null
                ) {
                    activity.window.decorView.setBackgroundColor(getColorFromAttr(editorContext, android.R.attr.colorBackground))
                }
                webView = WebEditorView(editorContext, activity.cacheDir.takeIf { initiallyBound })
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
