// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.utils.AssetHelper.guessMimeType
import com.ichi2.utils.withFileNameSafe
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.nio.file.Paths
import kotlin.io.path.pathString

private const val RANGE_HEADER = "Range"
private const val MATHJAX_PATH_PREFIX = "/_anki/js/vendor/mathjax"

// Match Anki's media-document policy: retain presentation, but prevent HTML/SVG media from
// executing scripts or reaching the parent card's APIs when loaded in an iframe or object.
private const val MEDIA_CONTENT_SECURITY_POLICY =
    "default-src 'none'; script-src 'none'; connect-src 'none'; object-src 'none'; " +
        "frame-src 'none'; child-src 'none'; base-uri 'none'; form-action 'none'; " +
        "style-src 'self' 'unsafe-inline'; img-src 'self'; font-src 'self'; media-src 'self'; " +
        "sandbox allow-same-origin"

class ViewerResourceHandler(
    context: Context,
    private val serverUrl: Uri,
) {
    private val assetManager = context.assets
    private val mediaDir = CollectionHelper.getMediaDirectory(context)

    fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
        val url = request.url
        val path = url.path

        if (request.method != "GET" || path == null || url.scheme != serverUrl.scheme ||
            url.encodedAuthority != serverUrl.encodedAuthority
        ) {
            return null
        }
        if (path == "/favicon.ico") {
            return WebResourceResponse(null, null, ByteArrayInputStream(ByteArray(0)))
        }

        try {
            if (path.startsWith(MATHJAX_PATH_PREFIX)) {
                val mathjaxAssetPath =
                    Paths
                        .get(
                            "backend/js/vendor/mathjax",
                            path.removePrefix(MATHJAX_PATH_PREFIX),
                        ).pathString
                val inputStream = assetManager.open(mathjaxAssetPath)
                return WebResourceResponse(guessMimeType(path), null, inputStream)
            }

            val file = mediaDir.withFileNameSafe(path)
            if (!file.exists()) {
                return null
            }
            val response =
                request.requestHeaders[RANGE_HEADER]?.let { range ->
                    handlePartialContent(file, range)
                } ?: WebResourceResponse(guessMimeType(path), null, FileInputStream(file))
            response.responseHeaders = response.responseHeaders.orEmpty() +
                ("Content-Security-Policy" to MEDIA_CONTENT_SECURITY_POLICY)
            return response
        } catch (e: SecurityException) {
            Timber.w("Path traversal attempt blocked")
            return null
        } catch (e: Exception) {
            Timber.d("File not found")
            return null
        }
    }

    @NeedsTest("seeking audio - 16513")
    private fun handlePartialContent(
        file: File,
        range: String,
    ): WebResourceResponse {
        val rangeHeader = RangeHeader.from(range, defaultEnd = file.length() - 1)

        val mimeType = guessMimeType(file.path)
        val (start, end) = rangeHeader
        val responseHeaders =
            mapOf(
                "Content-Range" to "bytes $start-$end/${file.length()}",
                "Accept-Ranges" to "bytes",
            )
        // WARN: WebResourceResponse appears to handle truncating the stream internally
        // This is NOT the same as NanoHTTPD

        // sending a truncated stream caused:
        // -> `net::ERR_FAILED`

        // returning a 'full' input stream with the provided header
        // returns a 'correct' Content-Length (example below)
        //
        // Content-Range: bytes 2916352-2931180/2931181
        // Content-Length: 14829
        // The above needs more investigation
        val fileStream = FileInputStream(file)
        return WebResourceResponse(
            mimeType,
            null,
            206,
            "Partial Content",
            responseHeaders,
            fileStream,
        )
    }
}

/**
 * Handles the "range" header in a HTTP Request
 */
data class RangeHeader(
    val start: Long,
    val end: Long,
) {
    companion object {
        fun from(
            range: String,
            defaultEnd: Long,
        ): RangeHeader {
            val numbers = range.substring("bytes=".length).split('-')
            val unspecifiedEnd = numbers.getOrNull(1).isNullOrEmpty()
            return RangeHeader(
                start = numbers[0].toLong(),
                end = if (unspecifiedEnd) defaultEnd else numbers[1].toLong(),
            )
        }
    }
}
