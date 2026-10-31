// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Kael Madar <itsybitsyspider@madarhome.com>

package com.ichi2.anki.common.utils

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.equalTo
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.io.File
import java.net.URI

class MissingMediaNotificationsTest {
    private lateinit var sut: MissingMediaNotifications
    private var timesCalled = 0
    private lateinit var fileNames: MutableList<String?>

    @Before
    fun before() {
        fileNames = ArrayList()
        sut = MissingMediaNotifications("127.0.0.1", "/jsapi/", 2)
    }

    private fun defaultHandler(): (String) -> Unit =
        { f: String? ->
            timesCalled++
            fileNames.add(f)
        }

    @Test
    fun firstTimeOnNewCardSends() {
        processFailure(getValidRequest("example.jpg"))
        assertThat(timesCalled, equalTo(1))
        assertThat(fileNames, contains("example.jpg"))
    }

    @Test
    fun twoCallsOnSameSideCallsOnce() {
        processFailure(getValidRequest("example.jpg"))
        processFailure(getValidRequest("example2.jpg"))
        assertThat(timesCalled, equalTo(1))
        assertThat(fileNames, contains("example.jpg"))
    }

    @Test
    fun callAfterFlipIsShown() {
        processFailure(getValidRequest("example.jpg"))
        sut.onCardSideChange()
        processFailure(getValidRequest("example2.jpg"))
        assertThat(timesCalled, equalTo(2))
        assertThat(fileNames, contains("example.jpg", "example2.jpg"))
    }

    @Test
    fun callAfterFlipSameImageIsIgnored() {
        processFailure(getValidRequest("example.jpg"))
        sut.onCardSideChange()
        processFailure(getValidRequest("example.jpg"))
        assertThat(timesCalled, equalTo(1))
        assertThat(fileNames, contains("example.jpg"))
    }

    @Test
    fun callAfterFlipDuplicateDoesNotHideDifferentImage() {
        processFailure(getValidRequest("example.jpg"))
        sut.onCardSideChange()
        processFailure(getValidRequest("example.jpg"))
        processFailure(getValidRequest("example2.jpg"))
        assertThat(fileNames, contains("example.jpg", "example2.jpg"))
    }

    @Test
    fun thirdCallIsIgnored() {
        processFailure(getValidRequest("example.jpg"))
        sut.onCardSideChange()
        processFailure(getValidRequest("example2.jpg"))
        sut.onCardSideChange()
        processFailure(getValidRequest("example3.jpg"))
        assertThat(timesCalled, equalTo(2))
        assertThat(fileNames, contains("example.jpg", "example2.jpg"))
    }

    @Test
    fun invalidRequestIsIgnored() {
        val invalidRequest = getInvalidRequest("example.jpg")
        processFailure(invalidRequest)
        assertThat(timesCalled, equalTo(0))
    }

    @Test
    fun jsApiFailureDoesNotHideMissingMediaOnSameSide() {
        processFailure(getRequestUri("http://127.0.0.1:40001/jsapi/cardDue"))
        processFailure(getValidRequest("example.jpg"))
        assertThat(fileNames, contains("example.jpg"))
    }

    @Test
    fun jsApiFailuresDoNotConsumeMissingMediaLimit() {
        processFailure(getRequestUri("http://127.0.0.1:40001/jsapi/cardDue"))
        sut.onCardSideChange()
        processFailure(getRequestUri("http://127.0.0.1:40001/jsapi/cardMod"))
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
        processFailure(getRequestUri("http://127.0.0.1:40001/jsapi/cardDue"))
        processFailure(getValidRequest("cardDue.bin"))
        sut.onCardSideChange()
        processFailure(getValidRequest("jsapi.jpg"))
        assertThat(fileNames, contains("cardDue.bin", "jsapi.jpg"))
    }

    @Test
    fun uiFailureDoesNotCrash() {
        processFailure(getValidRequest("example.jpg")) { throw RuntimeException("expected") }
        assertThat("Irrelevant assert to stop lint warnings", timesCalled, equalTo(0))
    }

    @Test
    fun imageIsNotRememberedWhenUiFails() {
        processFailure(getValidRequest("example.jpg")) { throw RuntimeException("expected") }
        sut.onCardSideChange()
        processFailure(getValidRequest("example.jpg"))
        assertThat(fileNames, contains("example.jpg"))
    }

    @Test
    fun repeatedMissingSoundOnSameSideIsShown() {
        val handler = defaultHandler()
        repeat(3) {
            processMissingMedia(File("example.wav"), handler)
        }
        assertThat(fileNames, contains("example.wav", "example.wav", "example.wav"))
    }

    @Test
    fun testThirdSoundIsIgnored() {
        // Tests that the third call to processMissingSound is ignored
        val handler = defaultHandler()
        processMissingMedia(File("example.wav"), handler)
        sut.onCardSideChange()
        processMissingMedia(File("example2.wav"), handler)
        sut.onCardSideChange()
        processMissingMedia(File("example3.wav"), handler)
        assertThat(timesCalled, equalTo(2))
        assertThat(fileNames, contains("example.wav", "example2.wav"))
    }

    @Test
    fun testMissingSound_ExceptionCaught() {
        assertDoesNotThrow { processMissingMedia(File("example.wav")) { throw RuntimeException("expected") } }
    }

    private fun processFailure(
        request: URI,
        consumer: (String) -> Unit = defaultHandler(),
    ) {
        sut.processFailure(request.host, request.path, { request.path.substringAfterLast('/') }, consumer)
    }

    private fun processMissingMedia(
        file: File,
        onFailure: (String) -> Unit,
    ) {
        sut.processMissingMedia(file, onFailure)
    }

    private fun getValidRequest(fileName: String): URI {
        // actual URL on Android 9
        val url = "http://127.0.0.1:40001/$fileName"
        return getRequestUri(url)
    }

    private fun getInvalidRequest(fileName: String): URI {
        // no collection.media in the URL
        val url = "file:///storage/emulated/0/AnkiDroid/$fileName"
        return getRequestUri(url)
    }

    private fun getRequestUri(url: String): URI = URI(url)
}
