// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.web

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.LocaleList
import android.util.Base64
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.getSystemService
import androidx.core.view.SoftwareKeyboardControllerCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.ichi2.anki.CollectionManager.withCol
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
// Created programmatically: a collection media root is required, so XML/tool constructors are not applicable.
@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
class WebEditorView(
    context: Context,
    mediaDirectory: File,
) : WebView(context) {
    var onReady: (() -> Unit)? = null
    var onChanged: ((WebEditorStatus) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onRendererGone: (() -> Unit)? = null
    var onMediaPaste: ((WebEditorTarget, Uri) -> Unit)? = null

    private val ready = CompletableDeferred<Unit>()
    private val pending = mutableMapOf<Int, CancellableContinuation<JSONObject>>()
    private var nextRequest = 0
    private var languageTag: String? = null
    private var destroyed = false
    private val bridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val scriptNonce = UUID.randomUUID().toString().replace("-", "")

    init {
        // WebView uses wrap-content height to choose its CSS viewport, even with exact Compose constraints.
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.setSupportMultipleWindows(false)
        val mediaRoot = mediaDirectory.canonicalFile
        val loader =
            WebViewAssetLoader
                .Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                .addPathHandler("/_app/") { path -> assetResponse("backend/sveltekit/app/$path") }
                .addPathHandler("/media/") { path ->
                    runCatching {
                        val file = File(mediaRoot, path).canonicalFile
                        if (file.parentFile == mediaRoot && file.isFile) {
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
        loadUrl("$ORIGIN/editor-fields")
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
                    .replace("</script", "<\\/script", ignoreCase = true)
            // The small trusted shell must run before Svelte starts. Inline it to avoid
            // two parser-blocking requests through WebView's asset interception.
            val shell =
                """<base href="/media/"><style>$style</style>""" +
                    """<script nonce="$scriptNonce">$script</script>"""
            val html =
                page
                    .replace(Regex("<script(?=\\s|>)"), "<script nonce=\"$scriptNonce\"")
                    .replace("<head>", "<head>$shell")
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
        request("loadDocument", document.toJson().put("resetBaseline", resetBaseline))
    }

    /** Loads fields and commits their recovery record in one bridge round trip. */
    suspend fun loadDocumentAndCreateDraft(
        document: WebEditorDocument,
        draftId: String,
        hostStateJson: String,
        resetBaseline: Boolean = true,
    ) {
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

    suspend fun focusField(index: Int = 0) {
        withContext(Dispatchers.Main.immediate) {
            requestFocus()
            request("focusField", JSONObject().put("index", index))
            SoftwareKeyboardControllerCompat(this@WebEditorView).show()
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

    suspend fun execute(
        action: WebEditorAction,
        target: WebEditorTarget? = null,
        value: String = "",
    ): Boolean =
        request(
            "execute",
            JSONObject().put("action", action.name).put("target", target?.toJson()).put("value", value),
        ).getBoolean("applied")

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
                    ),
                )
            "error" -> {
                val error = IllegalStateException(message.getString("message"))
                if (message.optBoolean("fatal")) fail(error) else onError?.invoke(error.message.orEmpty())
            }
            "hostRequest" -> handleHostRequest(message)
            "mediaPaste" -> {
                val target = message.getJSONObject("target").toTarget()
                val uri = Uri.parse(message.getString("uri"))
                onMediaPaste?.invoke(target, uri) ?: onError?.invoke("Use the media button to insert this file.")
            }
            "focus" -> {
                val tag = message.optString("languageTag").ifEmpty { null }
                if (tag != languageTag) {
                    languageTag = tag
                    context.getSystemService<InputMethodManager>()?.restartInput(this)
                }
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
        }

    private fun fail(exception: Exception) {
        ready.completeExceptionally(exception)
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

private fun WebEditorDocument.toJson(): JSONObject =
    JSONObject()
        .put("sessionId", sessionId)
        .put("generation", generation)
        .put("isCloze", isCloze)
        .put(
            "fields",
            JSONArray().apply {
                fields.forEach { field ->
                    put(
                        JSONObject()
                            .put("name", field.name)
                            .put("html", field.html)
                            .put("fontName", field.fontName)
                            .put("fontSize", field.fontSize)
                            .put("rtl", field.rtl)
                            .put("languageTag", field.languageTag)
                            .put("sticky", field.sticky)
                            .put("collapsed", field.collapsed)
                            .put("sourceMode", field.sourceMode),
                    )
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
    )
