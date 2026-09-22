// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import android.webkit.ValueCallback
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.core.view.isVisible
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.OnPageFinishedCallback
import com.ichi2.anki.utils.openUrl
import com.ichi2.anki.workarounds.SafeWebViewClient
import com.ichi2.anki.workarounds.SafeWebViewLayout
import com.ichi2.utils.AssetHelper.guessMimeType
import com.ichi2.utils.toRGBHex
import org.jsoup.Jsoup
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.MessageDigest

/**
 * Base WebViewClient to be used on [PageFragment]
 */
open class PageWebViewClient : SafeWebViewClient() {
    /** Set by the host before loading a page; never inferred from a navigation request. */
    internal var serverUrl: Uri? = null

    val onPageFinishedCallbacks: MutableList<OnPageFinishedCallback> = mutableListOf()

    private fun isInternalUrl(url: Uri): Boolean =
        serverUrl?.let { url.scheme == it.scheme && url.encodedAuthority == it.encodedAuthority } == true

    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?,
    ): Boolean {
        val url = request?.url ?: return true
        if (isInternalUrl(url)) {
            return !isSvelteKitPage(url.path.orEmpty().removePrefix("/"))
        }
        if (request.isForMainFrame && url.scheme in listOf("http", "https")) {
            view?.context?.openUrl(url)
        }
        return true
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        val path = request.url.path
        if (request.method != "GET" || path == null || !isInternalUrl(request.url)) return null
        if (path == "/favicon.png") {
            return WebResourceResponse("image/x-icon", null, ByteArrayInputStream(byteArrayOf()))
        }

        val assetPath =
            if (path.startsWith("/_app/")) {
                "backend/sveltekit/app/${path.substring(6)}"
            } else if (isSvelteKitPage(path.removePrefix("/"))) {
                "backend/sveltekit/index.html"
            } else {
                return null
            }

        try {
            val mimeType = guessMimeType(assetPath)
            val inputStream = view.context.assets.open(assetPath)
            if (assetPath == "backend/sveltekit/index.html") {
                val html = inputStream.bufferedReader().use { it.readText() }
                return pageResponse(html, request.url)
            }
            val response = WebResourceResponse(mimeType, null, inputStream)
            if ("immutable" in path) {
                response.responseHeaders = mapOf("Cache-Control" to "max-age=31536000")
            }
            return response
        } catch (_: IOException) {
            Timber.w("Not found %s", assetPath)
        }
        return null
    }

    /**
     * Anki's media server replaces the generated CSP according to the route. We serve the same
     * assets without that server, so must apply the policy here as well.
     */
    private fun pageResponse(
        html: String,
        url: Uri,
    ): WebResourceResponse {
        val document = Jsoup.parse(html)
        document.outputSettings().prettyPrint(false)
        document.select("meta[http-equiv=content-security-policy]").remove()
        val policy =
            if (url.path?.removePrefix("/")?.substringBefore("/") == "image-occlusion") {
                // Hash the bundled startup scripts. This also supports older backends whose HTML
                // predates the generated CSP, without allowing inline scripts from note content.
                val scriptHashes =
                    document.select("script:not([src])").joinToString(" ") {
                        val digest = MessageDigest.getInstance("SHA-256").digest(it.data().toByteArray(Charsets.UTF_8))
                        "'sha256-${Base64.encodeToString(digest, Base64.NO_WRAP)}'"
                    }
                val origin = "${url.scheme}://${url.encodedAuthority}"
                "script-src $origin/_app/ $origin/_anki/ $scriptHashes; form-action 'none'; frame-ancestors 'none'"
            } else {
                // Trusted pages use javascript: bridge links, including Custom Study and Unbury.
                "frame-ancestors 'none'"
            }
        return WebResourceResponse(
            "text/html",
            "UTF-8",
            200,
            "OK",
            mapOf("Content-Security-Policy" to policy),
            document.outerHtml().byteInputStream(Charsets.UTF_8),
        )
    }

    override fun onPageStarted(
        view: WebView?,
        url: String?,
        favicon: Bitmap?,
    ) {
        super.onPageStarted(view, url, favicon)
        view?.let { webView ->
            val bgColor = MaterialColors.getColor(webView, android.R.attr.colorBackground).toRGBHex()
            webView.evaluateAfterDOMContentLoaded(
                """document.body.style.setProperty("background-color", "$bgColor", "important");
                    console.log("Background color set");""",
            )
        }
    }

    /**
     * Shows the WebView after the page is loaded
     *
     * This may be overridden if additional 'screen ready' logic is provided by the backend
     * @see DeckOptions
     */
    open fun onShowWebView(webView: WebView) {
        Timber.v("Displaying WebView")
        webView.isVisible = true
        (webView.parent as? SafeWebViewLayout)?.isVisible = true
    }

    override fun onPageFinished(
        view: WebView?,
        url: String?,
    ) {
        super.onPageFinished(view, url)
        if (view == null) return
        onPageFinishedCallbacks.map { callback -> callback.onPageFinished(view) }
        /* webView is invisible by default to avoid flashes while
         * the page is loaded, and can be made visible again after it finishes loading */
        onShowWebView(view)
    }
}

fun isSvelteKitPage(path: String): Boolean {
    val pageName = path.substringBefore("/")
    return when (pageName) {
        "graphs",
        "congrats",
        "card-info",
        "change-notetype",
        "deck-options",
        "import-anki-package",
        "import-csv",
        "import-page",
        "image-occlusion",
        -> true
        else -> false
    }
}

fun WebView.evaluateAfterDOMContentLoaded(
    script: String,
    resultCallback: ValueCallback<String>? = null,
) {
    evaluateJavascript(
        """
        var codeToRun = function() { 
            $script
        }
        
        if (document.readyState === "loading") {
          document.addEventListener("DOMContentLoaded", codeToRun);
        } else {
          codeToRun();
        }
        """.trimIndent(),
        resultCallback,
    )
}
