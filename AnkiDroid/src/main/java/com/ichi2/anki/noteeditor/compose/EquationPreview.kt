// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.isInvisible
import androidx.webkit.WebViewAssetLoader
import com.ichi2.anki.R
import com.ichi2.anki.noteeditor.web.WebEditorAction
import org.json.JSONObject
import java.io.ByteArrayInputStream

/** Created only while the equation dialog is open; never part of editor startup. */
@Composable
internal fun EquationPreview(
    text: String,
    action: WebEditorAction,
) {
    val foreground = MaterialTheme.colorScheme.onSurface.toArgb()
    val error = MaterialTheme.colorScheme.error.toArgb()
    val unavailable = stringResource(R.string.compose_editor_equation_preview_unavailable)
    val empty = text.isBlank()
    val previewHeight = minOf(240.dp, LocalConfiguration.current.screenHeightDp.dp * 0.4f)
    Column {
        Text(
            stringResource(R.string.compose_editor_equation_preview),
            Modifier.padding(bottom = 8.dp),
            style = MaterialTheme.typography.labelMedium,
        )
        Surface(
            modifier = Modifier.fillMaxWidth().testTag("equation_preview"),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Box(Modifier.height(previewHeight), contentAlignment = Alignment.Center) {
                AndroidView(
                    factory = { EquationPreviewView(it) },
                    modifier = Modifier.matchParentSize(),
                    update = {
                        // Keep the renderer loaded, but hide stale output while an empty input clears.
                        it.isInvisible = empty
                        it.update(text, action, foreground, error, unavailable)
                    },
                    onRelease = { it.destroy() },
                )
                if (empty) {
                    Text(
                        stringResource(R.string.compose_editor_equation_preview_empty),
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private class EquationPreviewView(
    context: Context,
) : WebView(context) {
    private var ready = false
    private var input = ""

    init {
        // A wrap-content WebView has a zero-height CSS viewport even when Compose gives it a fixed height.
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setBackgroundColor(Color.TRANSPARENT)
        settings.javaScriptEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        val loader =
            WebViewAssetLoader
                .Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                .build()
        webViewClient =
            object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean = true

                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse =
                    loader.shouldInterceptRequest(request.url)
                        ?: WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))

                override fun onPageFinished(
                    view: WebView,
                    url: String,
                ) {
                    ready = true
                    render()
                }
            }
        loadUrl("https://appassets.androidplatform.net/assets/note-editor/equation-preview.html")
    }

    fun update(
        text: String,
        action: WebEditorAction,
        foreground: Int,
        error: Int,
        unavailable: String,
    ) {
        val next =
            JSONObject()
                .put("text", text)
                .put("kind", action.name)
                .put("foreground", "#%06x".format(foreground and 0xffffff))
                .put("error", "#%06x".format(error and 0xffffff))
                .put("unavailable", unavailable)
                .toString()
        if (next == input) return
        input = next
        render()
    }

    private fun render() {
        if (ready && input.isNotEmpty()) evaluateJavascript("window.setEquationPreview($input)", null)
    }
}
