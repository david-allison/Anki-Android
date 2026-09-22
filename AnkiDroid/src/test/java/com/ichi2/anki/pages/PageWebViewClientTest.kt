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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class PageWebViewClientTest : RobolectricTest() {
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
