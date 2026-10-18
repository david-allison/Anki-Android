// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.web

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.LocaleList
import android.text.InputType
import android.util.Base64
import android.util.TypedValue
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.material3.Typography
import androidx.compose.ui.unit.TextUnit
import androidx.core.content.getSystemService
import androidx.core.view.SoftwareKeyboardControllerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.common.utils.android.getColorFromAttr
import com.ichi2.utils.AssetHelper.guessMimeType
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URLConnection
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** The development editor's one field surface; native controls never mirror live field HTML. */
// Created programmatically and bound to a collection before accessing media or translations.
@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
class WebEditorView(
    context: Context,
    mediaDirectory: File? = null,
) : WebView(context) {
    var onReady: (() -> Unit)? = null
    var onChanged: ((WebEditorStatus) -> Unit)? = null
    var onFieldTap: (() -> Unit)? = null
    var onHeaderVisibility: ((Boolean) -> Unit)? = null
    var onEditEquation: ((WebEditorTarget, String, Boolean) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onRendererGone: (() -> Unit)? = null
    var onQuickAction: ((String, WebEditorTarget) -> Unit)? = null
    var onMediaPaste: ((WebEditorTarget, Uri) -> Unit)? = null

    private val ready = CompletableDeferred<Unit>()
    private val firstEditorInputConnection = CompletableDeferred<Unit>()
    private val pending = mutableMapOf<Int, CancellableContinuation<JSONObject>>()
    private var nextRequest = 0
    private var languageTag: String? = null
    private var focusedField: Int? = null
    private var languageHints: List<String?>? = null
    private var destroyed = false
    private val bridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val scriptNonce = UUID.randomUUID().toString().replace("-", "")
    private val collectionReady = CompletableDeferred<Unit>()
    private val hostBackgroundColor = getColorFromAttr(context, android.R.attr.colorBackground)
    private val hostForegroundColor = getColorFromAttr(context, com.google.android.material.R.attr.colorOnSurface)
    private val hostControlColor = getColorFromAttr(context, android.R.attr.colorControlNormal)

    // Compose maps this selected surface to primaryContainer for toolbar toggles.
    private val hostSelectedColor = getColorFromAttr(context, com.google.android.material.R.attr.colorSecondaryContainer)
    private val hostModeColor = getColorFromAttr(context, com.google.android.material.R.attr.colorSurfaceContainerHighest)
    private val hostStyle =
        ":root { --editor-host-background: ${hostBackgroundColor.toCssRgb()}; " +
            "--editor-host-foreground: ${hostForegroundColor.toCssRgb()}; " +
            "--editor-host-control: ${hostControlColor.toCssRgb()}; " +
            "--editor-host-selected-background: ${hostSelectedColor.toCssRgb()}; " +
            "--editor-host-selected-foreground: ${hostForegroundColor.toCssRgb()}; " +
            "--editor-host-mode-background: ${hostModeColor.toCssRgba()}; ${context.fieldNameCss(settings.textZoom)} }"

    @Volatile
    private var mediaRoot: File? = null

    init {
        setBackgroundColor(Color.TRANSPARENT)
        // WebView uses wrap-content height to choose its CSS viewport, even with exact Compose constraints.
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.setSupportMultipleWindows(false)
        mediaDirectory?.let(::bindCollection)
        val loader =
            WebViewAssetLoader
                .Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                .addPathHandler("/_app/") { path -> assetResponse("backend/sveltekit/app/$path") }
                .addPathHandler("/media/") { path ->
                    runCatching {
                        val root = mediaRoot ?: return@addPathHandler emptyResponse()
                        val file = File(root, path).canonicalFile
                        if (file.parentFile == root && file.isFile) {
                            WebResourceResponse(URLConnection.guessContentTypeFromName(file.name), null, file.inputStream())
                        } else {
                            emptyResponse()
                        }
                    }.getOrElse { emptyResponse() }
                }.build()
        webViewClient =
            object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean = true

                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse {
                    if (request.method != "GET") return emptyResponse()
                    if (request.url.scheme != "https" || request.url.authority != "appassets.androidplatform.net") return emptyResponse()
                    val response =
                        if (request.url.path == "/editor-fields") {
                            backendIndexResponse()
                        } else {
                            loader.shouldInterceptRequest(request.url) ?: return emptyResponse()
                        }
                    response.responseHeaders =
                        mapOf("Content-Security-Policy" to contentSecurityPolicy, "X-Content-Type-Options" to "nosniff")
                    return response
                }

                override fun onRenderProcessGone(
                    view: WebView,
                    detail: RenderProcessGoneDetail,
                ): Boolean {
                    onRendererGone?.invoke()
                    fail(IllegalStateException("The note editor stopped. Reopen it to recover its draft."))
                    (parent as? ViewGroup)?.removeView(this@WebEditorView)
                    destroy()
                    return true
                }
            }
        check(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            "Update Android System WebView to use the development note editor."
        }
        WebViewCompat.addWebMessageListener(this, "AnkiEditorHost", setOf(ORIGIN)) { _, message, _, mainFrame, _ ->
            if (!mainFrame || destroyed) return@addWebMessageListener
            try {
                handleMessage(JSONObject(message.data.orEmpty()))
            } catch (exception: Exception) {
                onError?.invoke(exception.message.orEmpty())
            }
        }
        // This app-wide list is owned here. The leading dot matches only our local asset
        // host, not its subdomains. Interception still rejects every non-local request.
        // Wait for registration so the first navigation benefits too; unsupported providers
        // and rejected registrations retain normal Safe Browsing checks.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ALLOWLIST)) {
            WebViewCompat.setSafeBrowsingAllowlist(setOf(".appassets.androidplatform.net")) {
                loadEditor()
            }
        } else {
            loadEditor()
        }
    }

    private fun loadEditor() {
        if (!destroyed) loadUrl("$ORIGIN/editor-fields")
    }

    /** Packaged assets can load immediately; collection resources wait for this one-time binding. */
    fun bindCollection(mediaDirectory: File) {
        check(!destroyed) { "The note editor is closed." }
        val root = mediaDirectory.canonicalFile
        check(mediaRoot == null || mediaRoot == root) { "The editor is already bound to another collection." }
        mediaRoot = root
        collectionReady.complete(Unit)
    }

    /** Trusted packaged bootstrap only; note HTML never receives a script nonce. */
    private fun backendIndexResponse(): WebResourceResponse =
        runCatching {
            val entryPoint =
                context.assets
                    .open("backend/editor-fields.json")
                    .bufferedReader()
                    .use { JSONObject(it.readText()).optString("entryPoint", "index.html") }
            val page =
                context.assets
                    .open("backend/sveltekit/$entryPoint")
                    .bufferedReader()
                    .use { it.readText() }
            val style =
                context.assets
                    .open("note-editor/editor.css")
                    .bufferedReader()
                    .use { it.readText() }
            val script =
                context.assets
                    .open("note-editor/editor.js")
                    .bufferedReader()
                    .use { it.readText() }
                    .let(::escapeInlineEditorScript)
            // The small trusted shell must run before Svelte starts. Inline it to avoid
            // two parser-blocking requests through WebView's asset interception.
            val shell =
                """<base href="/media/"><style>$hostStyle$style</style>""" +
                    """<script nonce="$scriptNonce">$script</script>"""
            val html =
                page
                    .replace(Regex("<script(?=\\s|>)"), "<script nonce=\"$scriptNonce\"")
                    .let { insertEditorShell(it, shell) }
            WebResourceResponse("text/html", "UTF-8", ByteArrayInputStream(html.toByteArray()))
        }.getOrElse { emptyResponse() }

    private fun assetResponse(path: String): WebResourceResponse? =
        runCatching {
            if (path.split('/').any { it == ".." }) return null
            WebResourceResponse(guessMimeType(path), null, context.assets.open(path))
        }.getOrNull()

    private val contentSecurityPolicy: String
        get() =
            "default-src 'none'; script-src 'self' 'nonce-$scriptNonce'; " +
                "style-src 'unsafe-inline' 'self'; img-src 'self' data: blob:; media-src 'self' data: blob:; " +
                "font-src 'self' data:; base-uri 'self'; form-action 'none'; frame-src 'none'; connect-src 'self'"

    suspend fun loadDocument(
        document: WebEditorDocument,
        resetBaseline: Boolean = true,
    ) {
        focusedField = null
        request("loadDocument", document.toJson().put("resetBaseline", resetBaseline))
    }

    /** Loads fields and commits their recovery record in one bridge round trip. */
    suspend fun loadDocumentAndCreateDraft(
        document: WebEditorDocument,
        draftId: String,
        hostStateJson: String,
        resetBaseline: Boolean = true,
    ) {
        focusedField = null
        request(
            "loadDocumentAndCreateDraft",
            JSONObject()
                .put("document", document.toJson().put("resetBaseline", resetBaseline))
                .put("draftId", draftId)
                .put("hostStateJson", hostStateJson),
        )
    }

    /** A brief native Save/type transition can disable editing while snapshots remain available. */
    suspend fun setInputEnabled(enabled: Boolean) {
        request("setInputEnabled", JSONObject().put("enabled", enabled))
    }

    /** A blank tap dismisses native field options without blurring the editable field. */
    fun setFieldOptionsVisible(visible: Boolean) {
        if (!destroyed) evaluateJavascript("AnkiEditor.setFieldOptionsVisible($visible)", null)
    }

    /** Clip the covered web content in Chromium too, including its native selection handles. */
    fun setFieldOptionsTop(topInWindow: Float) {
        if (destroyed) return
        val location = IntArray(2)
        getLocationInWindow(location)
        val visibleHeight = (topInWindow - location[1]).toInt().coerceIn(0, height)
        evaluateJavascript("AnkiEditor.clipFieldOptions($visibleHeight)", null)
    }

    /** Apply note-type hints without rebuilding fields, losing selection or resetting undo. */
    fun updateLanguageHints(hints: List<String?>) {
        languageHints = hints
        focusedField?.let { updateInputLanguage(hints.getOrNull(it)) }
    }

    private fun updateInputLanguage(tag: String?) {
        if (tag == languageTag) return
        languageTag = tag
        context.getSystemService<InputMethodManager>()?.restartInput(this)
    }

    suspend fun focusField(
        index: Int = 0,
        highlightError: Boolean = false,
        showKeyboard: Boolean = true,
        revealAboveOptions: Boolean = false,
    ) {
        withContext(Dispatchers.Main.immediate) {
            requestFocus()
            request(
                "focusField",
                JSONObject().put("index", index).put("highlightError", highlightError).put("revealAboveOptions", revealAboveOptions),
            )
            // The JS focus acknowledgement can arrive before Chromium supplies the native
            // input connection. Opening Gboard with the dummy connection changes its height
            // mid-animation, making the toolbar jump when the real field becomes available.
            if (showKeyboard) {
                firstEditorInputConnection.await()
                SoftwareKeyboardControllerCompat(this@WebEditorView).show()
            } else {
                SoftwareKeyboardControllerCompat(this@WebEditorView).hide()
            }
        }
    }

    val keyboardVisible: Boolean
        get() = ViewCompat.getRootWindowInsets(this)?.isVisible(WindowInsetsCompat.Type.ime()) == true

    suspend fun captureFocus(): WebEditorFocusState {
        val visible = keyboardVisible
        return WebEditorFocusState(captureTarget(), visible)
    }

    /** Return to a bookmarked selection, or retain the caret left by an applied command. */
    suspend fun restoreFocus(
        target: WebEditorTarget? = null,
        showKeyboard: Boolean,
    ) {
        withContext(Dispatchers.Main.immediate) {
            // onDismiss/onActivityResult can run while the other window still owns the IME.
            if (!hasWindowFocus()) {
                suspendCancellableCoroutine { continuation ->
                    val observer = viewTreeObserver
                    val listener =
                        object : ViewTreeObserver.OnWindowFocusChangeListener {
                            override fun onWindowFocusChanged(hasFocus: Boolean) {
                                if (!hasFocus) return
                                observer.removeOnWindowFocusChangeListener(this)
                                continuation.resume(Unit)
                            }
                        }
                    observer.addOnWindowFocusChangeListener(listener)
                    continuation.invokeOnCancellation {
                        if (observer.isAlive) observer.removeOnWindowFocusChangeListener(listener)
                    }
                }
            }
            // Anki's selectionText validates and restores the bookmark without changing the HTML.
            if (target != null && selectionText(target) == null) return@withContext
            requestFocus()
            SoftwareKeyboardControllerCompat(this@WebEditorView).run {
                if (showKeyboard) show() else hide()
            }
        }
    }

    suspend fun snapshot(): WebEditorSnapshot {
        val value = request("snapshot")
        return WebEditorSnapshot(
            value.getString("sessionId"),
            value.getInt("generation"),
            value.getInt("revision"),
            value.getJSONArray("fields").strings(),
            value.getBoolean("hasChanges"),
        )
    }

    suspend fun captureTarget(): WebEditorTarget? {
        val value = request("captureTarget")
        if (!value.has("bookmark")) return null
        return WebEditorTarget(
            value.getString("sessionId"),
            value.getInt("generation"),
            value.getInt("field"),
            value.getInt("revision"),
            value.getString("bookmark"),
        )
    }

    suspend fun selectionText(target: WebEditorTarget): String? =
        request("selectionText", target.toJson()).let { if (it.isNull("text")) null else it.getString("text") }

    suspend fun execute(
        action: WebEditorAction,
        target: WebEditorTarget? = null,
        value: String = "",
    ): Boolean =
        request(
            "execute",
            JSONObject().put("action", action.name).put("target", target?.toJson()).put("value", value),
        ).getBoolean("applied")

    suspend fun updateFieldTypography(fonts: List<WebEditorFieldTypography>) {
        request(
            "setFieldTypography",
            JSONObject().put(
                "fonts",
                JSONArray(
                    fonts.map {
                        JSONObject().put("fontName", it.fontName).put("fontSize", it.fontSize).put("rtl", it.rtl)
                    },
                ),
            ),
        )
    }

    suspend fun updateFieldDescriptions(descriptions: List<String>) {
        request("setFieldDescriptions", JSONObject().put("descriptions", JSONArray(descriptions)))
    }

    suspend fun updateFieldActions(actions: List<List<WebEditorFieldAction>>) {
        request("setFieldActions", JSONObject().put("actions", JSONArray(actions.map { it.toJsonActions() })))
    }

    suspend fun updateSettings(settings: WebEditorSettings) {
        check(request("setSettings", settings.toJson()).getBoolean("applied")) {
            context.getString(R.string.compose_editor_finish_composition)
        }
    }

    suspend fun createDraft(
        draftId: String,
        hostStateJson: String,
    ) {
        request("createDraft", JSONObject().put("draftId", draftId).put("hostStateJson", hostStateJson))
    }

    suspend fun updateHostState(hostStateJson: String) {
        request("updateHostState", JSONObject().put("hostStateJson", hostStateJson))
    }

    /** Restores fields and their clean baseline together. The caller restores native controls. */
    suspend fun restoreDraft(draftId: String): WebEditorDraft? {
        val value = request("restoreDraft", JSONObject().put("draftId", draftId))
        if (!value.has("document")) return null
        return WebEditorDraft(
            value.getJSONObject("document").toDocument(),
            value.getJSONArray("baseline").strings(),
            value.getString("hostStateJson"),
        )
    }

    suspend fun discardDraft(draftId: String) {
        request("discardDraft", JSONObject().put("draftId", draftId))
    }

    private suspend fun request(
        method: String,
        arguments: JSONObject = JSONObject(),
    ): JSONObject =
        withContext(Dispatchers.Main.immediate) {
            withTimeout(10_000) {
                check(!destroyed) { "The note editor is closed." }
                ready.await()
                suspendCancellableCoroutine { continuation ->
                    val id = ++nextRequest
                    pending[id] = continuation
                    continuation.invokeOnCancellation { post { pending.remove(id) } }
                    evaluateJavascript("AnkiEditor.request($id, ${JSONObject.quote(method)}, $arguments)", null)
                }
            }
        }

    private fun handleMessage(message: JSONObject) {
        when (message.getString("type")) {
            "fieldTap" -> onFieldTap?.invoke()
            "headerVisibility" -> onHeaderVisibility?.invoke(message.getBoolean("visible"))
            "editEquation" ->
                onEditEquation?.invoke(
                    message.getJSONObject("target").toTarget(),
                    message.getString("text"),
                    message.getBoolean("block"),
                )
            "ready" -> {
                ready.complete(Unit)
                onReady?.invoke()
            }
            "result" -> {
                val continuation = pending.remove(message.getInt("id")) ?: return
                if (message.has("error")) {
                    continuation.resumeWithException(IllegalStateException(message.getString("error")))
                } else {
                    continuation.resume(message.optJSONObject("value") ?: JSONObject())
                }
            }
            "changed" ->
                onChanged?.invoke(
                    WebEditorStatus(
                        message.getInt("revision"),
                        message.getBoolean("hasChanges"),
                        message.optBoolean("composing"),
                        message.optBoolean("hasSelection"),
                        message.optInt("activeField", -1).takeIf { it >= 0 },
                        message.optBoolean("sourceMode"),
                        message
                            .optJSONArray("activeFormats")
                            ?.let { formats ->
                                buildSet {
                                    for (index in 0 until formats.length()) {
                                        WebEditorAction.entries.firstOrNull { it.name == formats.optString(index) }?.let(::add)
                                    }
                                }
                            }.orEmpty(),
                        message.optBoolean("canUndo"),
                        message.optBoolean("canRedo"),
                    ),
                )
            "error" -> {
                val error = IllegalStateException(message.getString("message"))
                if (message.optBoolean("fatal")) fail(error) else onError?.invoke(error.message.orEmpty())
            }
            "hostRequest" -> handleHostRequest(message)
            "quickAction" -> onQuickAction?.invoke(message.getString("action"), message.getJSONObject("target").toTarget())
            "mediaPaste" -> {
                val target = message.getJSONObject("target").toTarget()
                val uri = Uri.parse(message.getString("uri"))
                onMediaPaste?.invoke(target, uri) ?: onError?.invoke("Use the media button to insert this file.")
            }
            "focus" -> {
                focusedField = message.optInt("field", -1).takeIf { it >= 0 }
                val hints = languageHints
                updateInputLanguage(
                    if (hints != null) focusedField?.let(hints::getOrNull) else message.optString("languageTag").ifEmpty { null },
                )
            }
        }
    }

    /** This field surface exposes no collection mutation or generic backend dispatch. */
    private fun handleHostRequest(message: JSONObject) {
        val id = message.getInt("id")
        bridgeScope.launch {
            try {
                val response =
                    when (message.getString("method")) {
                        "i18nResources" -> {
                            val encoded = message.getString("bytes")
                            require(encoded.length <= 16_384) { "Invalid translation request." }
                            collectionReady.await()
                            val bytes = withCol { i18nResourcesRaw(Base64.decode(encoded, Base64.DEFAULT)) }
                            JSONObject().put("bytes", Base64.encodeToString(bytes, Base64.NO_WRAP))
                        }
                        "clipboard" -> {
                            val item =
                                context
                                    .getSystemService<ClipboardManager>()
                                    ?.primaryClip
                                    ?.takeIf { it.itemCount > 0 }
                                    ?.getItemAt(0)
                            JSONObject().apply {
                                item?.text?.let { put("text", it.toString()) }
                                item?.htmlText?.let { put("html", it) }
                                item?.uri?.let { put("uri", it.toString()) }
                            }
                        }
                        else -> error("This editor only exposes fields and translations.")
                    }
                if (!destroyed) evaluateJavascript("AnkiEditor.hostResponse($id, $response, null)", null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!destroyed) {
                    evaluateJavascript(
                        "AnkiEditor.hostResponse($id, null, ${JSONObject.quote(e.localizedMessage ?: e.toString())})",
                        null,
                    )
                }
            }
        }
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? =
        super.onCreateInputConnection(outAttrs).also {
            outAttrs.hintLocales = languageTag?.let { LocaleList(Locale.forLanguageTag(it)) }
            if (it != null && outAttrs.inputType != InputType.TYPE_NULL && !firstEditorInputConnection.isCompleted) {
                // Let InputMethodManager receive the connection before resuming the show request.
                post { firstEditorInputConnection.complete(Unit) }
            }
        }

    private fun fail(exception: Exception) {
        ready.completeExceptionally(exception)
        firstEditorInputConnection.completeExceptionally(exception)
        val requests = pending.values.toList()
        pending.clear()
        requests.forEach { it.resumeWithException(exception) }
        onError?.invoke(exception.message.orEmpty())
    }

    override fun destroy() {
        if (destroyed) return
        destroyed = true
        onReady = null
        onChanged = null
        onError = null
        onRendererGone = null
        onMediaPaste = null
        bridgeScope.cancel()
        fail(IllegalStateException("The note editor is closed."))
        super.destroy()
    }

    companion object {
        const val ORIGIN = "https://appassets.androidplatform.net"

        private fun emptyResponse() =
            WebResourceResponse("text/plain", "UTF-8", 404, "Not found", emptyMap(), ByteArrayInputStream(byteArrayOf()))
    }
}

/** AnkiDroidTheme uses Material's default type scale; share titleMedium with its native field headings. */
private fun Context.fieldNameCss(textZoom: Int): String {
    val style = Typography().titleMedium
    val metrics = resources.displayMetrics

    // CSS pixels are dp in this viewport. Android resolves sp, including nonlinear font scaling.
    fun cssPixels(value: TextUnit) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value.value, metrics) / metrics.density

    val fontSize = cssPixels(style.fontSize)
    // WebView applies its own linear text zoom. Compensate only the heading, leaving note HTML alone.
    return "--editor-field-name-size: ${fontSize / (textZoom / 100f)}px; " +
        "--editor-field-name-weight: ${style.fontWeight?.weight ?: 400}; " +
        "--editor-field-name-line-height: ${cssPixels(style.lineHeight) / fontSize}; " +
        "--editor-field-name-letter-spacing: ${cssPixels(style.letterSpacing)}px;"
}

private fun Int.toCssRgb(): String = "rgb(${Color.red(this)}, ${Color.green(this)}, ${Color.blue(this)})"

private fun Int.toCssRgba(): String = "rgba(${Color.red(this)}, ${Color.green(this)}, ${Color.blue(this)}, ${Color.alpha(this) / 255f})"

/** Most packaged scripts contain no end tags, so avoid scanning every character with case folding. */
internal fun escapeInlineEditorScript(script: String): String =
    if ("</" in script) script.replace("</script", "<\\/script", ignoreCase = true) else script

/** String insertion uses bulk copies instead of Android's per-character CharSequence range append. */
internal fun insertEditorShell(
    page: String,
    shell: String,
): String {
    val head = page.indexOf("<head>")
    return if (head < 0) page else StringBuilder(page).insert(head + "<head>".length, shell).toString()
}

private fun WebEditorDocument.toJson(): JSONObject =
    JSONObject()
        .put("sessionId", sessionId)
        .put("generation", generation)
        .put("isCloze", isCloze)
        .put("settings", settings.toJson())
        .put("baselineFieldMap", baselineFieldMap?.let(::JSONArray))
        .put(
            "fields",
            JSONArray().apply {
                fields.forEach { field ->
                    put(
                        JSONObject()
                            .put("description", field.description)
                            .put("name", field.name)
                            .put("html", field.html)
                            .put("fontName", field.fontName)
                            .put("fontSize", field.fontSize)
                            .put("rtl", field.rtl)
                            .put("languageTag", field.languageTag)
                            .put("sticky", field.sticky)
                            .put("collapsed", field.collapsed)
                            .put("sourceMode", field.sourceMode)
                            .apply { if (field.quickActions.isNotEmpty()) put("quickActions", field.quickActions.toJsonActions()) },
                    )
                }
            },
        )

private fun List<WebEditorFieldAction>.toJsonActions(): JSONArray =
    JSONArray(
        map { action ->
            JSONObject()
                .put("id", action.id)
                .put("label", action.label)
                .put("iconPath", action.iconPath)
                .put("iconViewBox", action.iconViewBox)
                .put("active", action.active)
                .apply {
                    action.richText?.let {
                        put(
                            "richText",
                            JSONObject()
                                .put("label", it.label)
                                .put("iconPath", it.iconPath)
                                .put("iconViewBox", it.iconViewBox),
                        )
                    }
                }
        },
    )

private fun WebEditorTarget.toJson(): JSONObject =
    JSONObject()
        .put("sessionId", sessionId)
        .put("generation", generation)
        .put("field", field)
        .put("revision", revision)
        .put("bookmark", bookmark)

private fun JSONObject.toTarget(): WebEditorTarget =
    WebEditorTarget(getString("sessionId"), getInt("generation"), getInt("field"), getInt("revision"), getString("bookmark"))

private fun JSONArray.strings(): List<String> = (0 until length()).map(::getString)

private fun JSONObject.toDocument(): WebEditorDocument =
    WebEditorDocument(
        getString("sessionId"),
        getInt("generation"),
        getJSONArray("fields").let { fields ->
            (0 until fields.length()).map { index ->
                val field = fields.getJSONObject(index)
                WebEditorField(
                    name = field.getString("name"),
                    description = field.optString("description"),
                    html = field.getString("html"),
                    fontName = field.getString("fontName"),
                    fontSize = field.getInt("fontSize"),
                    rtl = field.getBoolean("rtl"),
                    languageTag = field.optString("languageTag").ifEmpty { null },
                    sticky = field.getBoolean("sticky"),
                    collapsed = field.getBoolean("collapsed"),
                    sourceMode = field.getBoolean("sourceMode"),
                )
            }
        },
        optBoolean("isCloze"),
        WebEditorSettings.fromJson(optJSONObject("settings")),
    )
