// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Kael Madar <itsybitsyspider@madarhome.com>

package com.ichi2.anki.cardviewer

import android.net.Uri
import android.webkit.WebResourceRequest
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.pages.PostRequestBridge
import com.ichi2.testutils.EmptyApplication
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.equalTo
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class MediaErrorHandlerTest {
    private lateinit var sut: MediaErrorHandler
    private var timesCalled = 0
    private lateinit var fileNames: MutableList<String?>

    @Before
    fun before() {
        fileNames = ArrayList()
        sut = MediaErrorHandler()
    }

    private fun defaultHandler(): (String) -> Unit =
        { f: String? ->
            timesCalled++
            fileNames.add(f)
        }

    @Test
    fun `WebView media filenames are decoded by the Android adapter`() {
        processFailure(getWebResourceRequest("${PostRequestBridge.BASE_URL}%E5%A5%BD%20name.jpg"))

        assertThat(fileNames, contains("好 name.jpg"))
    }

    @Test
    fun jsApiFailuresAreNotReportedAsMissingMedia() {
        for (method in listOf("POST", "GET", "OPTIONS")) {
            for (endpoint in listOf("cardDue", "cardMod", "ttsSetLanguage", "showAnswer?test=1")) {
                processFailure(getWebResourceRequest("${PostRequestBridge.BASE_URL}jsapi/$endpoint", method))
                assertThat("$method $endpoint", timesCalled, equalTo(0))
            }
        }
    }

    @Test
    fun jsApiFailureDoesNotHideMissingMediaOnSameSide() {
        processFailure(getWebResourceRequest("${PostRequestBridge.BASE_URL}jsapi/cardDue", "POST"))
        processFailure(getValidRequest("example.jpg"))
        assertThat(fileNames, contains("example.jpg"))
    }

    @Test
    fun jsApiFailuresDoNotConsumeMissingMediaLimit() {
        processFailure(getWebResourceRequest("${PostRequestBridge.BASE_URL}jsapi/cardDue", "POST"))
        sut.onCardSideChange()
        processFailure(getWebResourceRequest("${PostRequestBridge.BASE_URL}jsapi/cardMod", "POST"))
        sut.onCardSideChange()
        processFailure(getValidRequest("example.jpg"))
        sut.onCardSideChange()
        processFailure(getValidRequest("example2.jpg"))
        sut.onCardSideChange()
        processFailure(getValidRequest("example3.jpg"))
        assertThat(fileNames, contains("example.jpg", "example2.jpg"))
    }

    @Test
    fun mediaWithApiLikeNamesIsStillReported() {
        processFailure(getWebResourceRequest("${PostRequestBridge.BASE_URL}jsapi/cardDue", "POST"))
        processFailure(getValidRequest("cardDue.bin"))
        sut.onCardSideChange()
        processFailure(getValidRequest("jsapi.jpg"))
        assertThat(fileNames, contains("cardDue.bin", "jsapi.jpg"))
    }

    private fun processFailure(
        invalidRequest: WebResourceRequest,
        consumer: (String) -> Unit = defaultHandler(),
    ) {
        sut.processFailure(invalidRequest, consumer)
    }

    private fun getValidRequest(fileName: String): WebResourceRequest {
        val url = "${PostRequestBridge.BASE_URL}$fileName"
        return getWebResourceRequest(url)
    }

    private fun getInvalidRequest(fileName: String): WebResourceRequest {
        // no collection.media in the URL
        val url = "file:///storage/emulated/0/AnkiDroid/$fileName"
        return getWebResourceRequest(url)
    }

    private fun getWebResourceRequest(
        url: String,
        method: String = "GET",
    ): WebResourceRequest =
        object : WebResourceRequest {
            override fun getUrl(): Uri = url.toUri()

            override fun isForMainFrame(): Boolean = false

            override fun isRedirect(): Boolean = false

            override fun hasGesture(): Boolean = false

            override fun getMethod(): String = method

            override fun getRequestHeaders(): Map<String, String>? = null
        }
}
