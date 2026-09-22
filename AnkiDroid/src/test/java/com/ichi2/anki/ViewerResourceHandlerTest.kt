// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.webkit.WebResourceRequest
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.storage.CollectionHelper
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class ViewerResourceHandlerTest : RobolectricTest() {
    private val mediaDir: File
        get() = CollectionHelper.getMediaDirectory(targetContext).apply { mkdirs() }

    @Test
    fun `file in the media directory is served`() {
        File(mediaDir, "hello.txt").writeText("hello")

        val response = ViewerResourceHandler(targetContext, "http://127.0.0.1/".toUri()).shouldInterceptRequest(request("/hello.txt"))

        assertEquals("hello", assertNotNull(response).data.reader().readText())
    }

    @Test
    fun `path traversal outside the media directory is blocked`() {
        File(mediaDir.parentFile, "outside.txt").writeText("secret")

        val response = ViewerResourceHandler(targetContext, "http://127.0.0.1/".toUri()).shouldInterceptRequest(request("/../outside.txt"))

        assertNull(response, "a request escaping the media directory must not be served")
    }

    @Test
    fun `media documents cannot execute scripts or submit forms`() {
        File(mediaDir, "active.html").writeText("<script>parent.compromised = true</script>")
        val response =
            assertNotNull(ViewerResourceHandler(targetContext, "http://127.0.0.1/".toUri()).shouldInterceptRequest(request("/active.html")))

        val policy = assertNotNull(response.responseHeaders?.get("Content-Security-Policy"))
        for (directive in listOf(
            "script-src 'none'",
            "form-action 'none'",
            "sandbox allow-same-origin",
            "style-src 'self' 'unsafe-inline'",
            "img-src 'self'",
        )) {
            kotlin.test.assertTrue(directive in policy, directive)
        }
    }

    @Test
    fun `external requests cannot be supplied with collection media`() {
        File(mediaDir, "hello.txt").writeText("private media")
        val handler = ViewerResourceHandler(targetContext, "http://127.0.0.1/".toUri())
        for (origin in listOf("https://127.0.0.1", "http://127.0.0.1:1", "https://example.org", "http://127.0.0.1.evil.example")) {
            val request =
                mock<WebResourceRequest> {
                    on { url } doReturn "$origin/hello.txt".toUri()
                    on { method } doReturn "GET"
                    on { requestHeaders } doReturn emptyMap()
                }
            assertNull(handler.shouldInterceptRequest(request), origin)
        }
    }

    private fun request(path: String): WebResourceRequest =
        mock {
            on { url } doReturn "http://127.0.0.1$path".toUri()
            on { method } doReturn "GET"
            on { requestHeaders } doReturn emptyMap()
        }
}
