// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response.Status
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.InputStream
import java.net.SocketTimeoutException
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class AnkiServerTest {
    @Test
    fun `only requests from the server origin reach the backend`() {
        var calls = 0
        val handler =
            object : PostRequestHandler {
                override suspend fun handlePostRequest(
                    uri: PostRequestUri,
                    bytes: ByteArray,
                ): ByteArray {
                    calls++
                    return byteArrayOf()
                }
            }
        val server = AnkiServer(handler).apply { start() }
        try {
            val origin = server.baseUrl().removeSuffix("/")
            val authority = origin.removePrefix("http://")
            val invalidHeaders =
                listOf(
                    mapOf("host" to authority, "origin" to "https://example.org"),
                    mapOf("host" to authority, "origin" to "$origin.evil.example"),
                    mapOf("host" to authority, "origin" to "http://127.0.0.1:1"),
                    mapOf("host" to authority, "origin" to origin.replace("http:", "https:")),
                    mapOf("host" to authority, "origin" to "null"),
                    mapOf("host" to authority),
                    mapOf("host" to "evil.example", "origin" to origin),
                )
            for (headers in invalidHeaders) {
                assertEquals(Status.FORBIDDEN, server.serve(postSession(headers)).status, headers.toString())
            }
            assertEquals(0, calls, "rejected requests must not mutate the collection")
            assertEquals(Status.OK, server.serve(postSession(mapOf("host" to authority, "origin" to origin))).status)
            assertEquals(1, calls)
        } finally {
            server.stop()
        }
    }

    private fun postSession(requestHeaders: Map<String, String>): IHTTPSession =
        mock {
            on { method } doReturn Method.POST
            on { uri } doReturn "/_anki/updateDeckConfigs"
            on { headers } doReturn requestHeaders + ("content-length" to "0")
            on { inputStream } doReturn ByteArrayInputStream(byteArrayOf())
        }

    @Test
    fun `a request body can arrive in several reads`() {
        val bytes = ByteArray(100) { it.toByte() }
        val stream =
            object : ByteArrayInputStream(bytes) {
                override fun read(
                    buffer: ByteArray,
                    offset: Int,
                    length: Int,
                ): Int = super.read(buffer, offset, minOf(length, 3))
            }
        val session = postSession(stream, contentLength = bytes.size)

        assertContentEquals(bytes, AnkiServer.getSessionBytes(session))
    }

    @Test
    fun `a truncated request propagates to NanoHTTPD without dispatching`() {
        val handler = mock<PostRequestHandler>()
        val server = AnkiServer(handler).apply { start() }
        try {
            val session = postSession(ByteArrayInputStream(byteArrayOf(1, 2)), contentLength = 10, requestHeaders = originHeaders(server))

            assertFailsWith<EOFException> { server.serve(session) }
            verifyNoInteractions(handler)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `a body read timeout propagates to NanoHTTPD without dispatching`() {
        val timeout = SocketTimeoutException("Read timed out")
        val stream =
            object : InputStream() {
                override fun read(): Int = throw timeout
            }
        val handler = mock<PostRequestHandler>()
        val server = AnkiServer(handler).apply { start() }
        try {
            val session = postSession(stream, contentLength = 1, requestHeaders = originHeaders(server))

            assertSame(timeout, assertFailsWith<SocketTimeoutException> { server.serve(session) })
            verifyNoInteractions(handler)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `reading a body leaves bytes beyond content length unread`() {
        val stream = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4))
        val session = postSession(stream, contentLength = 3)

        assertContentEquals(byteArrayOf(1, 2, 3), AnkiServer.getSessionBytes(session))
        assertEquals(4, stream.read())
    }

    @Test
    fun `an empty protobuf request is accepted`() {
        val session = postSession(ByteArrayInputStream(byteArrayOf()), contentLength = 0)

        assertContentEquals(byteArrayOf(), AnkiServer.getSessionBytes(session))
    }

    private fun postSession(
        stream: InputStream,
        contentLength: Int,
        requestHeaders: Map<String, String> = emptyMap(),
    ): IHTTPSession =
        mock {
            on { method } doReturn Method.POST
            on { uri } doReturn "/_anki/test"
            on { headers } doReturn requestHeaders + ("content-length" to contentLength.toString())
            on { inputStream } doReturn stream
        }

    private fun originHeaders(server: AnkiServer): Map<String, String> {
        val origin = server.baseUrl().removeSuffix("/")
        return mapOf("host" to origin.removePrefix("http://"), "origin" to origin)
    }
}
