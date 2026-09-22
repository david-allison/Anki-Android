// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.core.net.toUri
import androidx.core.view.children
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.common.destinations.StatisticsDestination
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class PageWebViewClientTest : RobolectricTest() {
    @Test
    fun `trusted pages allow their bridge links but cannot be framed`() =
        withStatistics { view, client ->
            val response = assertNotNull(client.shouldInterceptRequest(view, request(assertNotNull(view.url))))
            assertEquals("frame-ancestors 'none'", response.responseHeaders["Content-Security-Policy"])
            val html = response.data.bufferedReader().use { it.readText() }
            assertFalse(html.contains("http-equiv=\"content-security-policy\"", ignoreCase = true))
        }

    @Test
    fun `image occlusion permits bundled scripts but restricts note content`() =
        withStatistics { view, client ->
            val address =
                assertNotNull(view.url)
                    .toUri()
                    .buildUpon()
                    .path("/image-occlusion/1")
                    .build()
            val response = assertNotNull(client.shouldInterceptRequest(view, request(address.toString())))
            val policy = assertNotNull(response.responseHeaders["Content-Security-Policy"])
            assertTrue(policy.contains("http://${address.encodedAuthority}/_app/"))
            assertTrue(policy.contains("'sha256-"), "the bundled startup script must remain executable")
            assertTrue(policy.contains("form-action 'none'"))
            assertTrue(policy.contains("frame-ancestors 'none'"))
            assertFalse(policy.contains("'self'"), "media must not become executable just because it is local")
            assertFalse(policy.contains("'unsafe-inline'"))
        }

    @Test
    fun `only the page server can serve bundled pages`() =
        withStatistics { view, client ->
            val url = assertNotNull(view.url).toUri()
            assertNotNull(client.shouldInterceptRequest(view, request(url.toString())))

            val otherOrigins =
                listOf(
                    url.buildUpon().scheme("https").build(),
                    url.buildUpon().encodedAuthority("127.0.0.1:${url.port + 1}").build(),
                    url.buildUpon().encodedAuthority("example.org").build(),
                    url.buildUpon().encodedAuthority("user@${url.encodedAuthority}").build(),
                )
            for (other in otherOrigins) {
                assertNull(client.shouldInterceptRequest(view, request(other.toString())), other.toString())
            }
        }

    @Test
    fun `external navigation is handled outside the page`() =
        withStatistics { view, client ->
            assertTrue(client.shouldOverrideUrlLoading(view, request("https://example.org/graphs")))
            assertFalse(client.shouldOverrideUrlLoading(view, request(assertNotNull(view.url))))
        }

    @Test
    fun `external frames cannot navigate to a bundled page`() =
        withStatistics { view, client ->
            assertTrue(client.shouldOverrideUrlLoading(view, request("https://example.org/graphs", mainFrame = false)))
        }

    private fun withStatistics(block: (WebView, PageWebViewClient) -> Unit) {
        val activity =
            startActivityNormallyOpenCollectionWithIntent(
                SingleFragmentActivity::class.java,
                StatisticsDestination.toIntent(targetContext),
            )
        advanceRobolectricLooper()
        val page = activity.fragment as Statistics
        val view =
            page.webViewLayout.children
                .filterIsInstance<WebView>()
                .single()
        block(view, view.webViewClient as PageWebViewClient)
    }

    private fun request(
        address: String,
        mainFrame: Boolean = true,
    ): WebResourceRequest =
        mock {
            on { url } doReturn address.toUri()
            on { method } doReturn "GET"
            on { isForMainFrame } doReturn mainFrame
        }
}
