// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils.nativefiles

import com.google.common.io.FileWriteMode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.nio.file.Files
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class NativeResourceFilesTest {
    @TempDir
    lateinit var directory: File

    @Test
    fun `cached file copies are exact and independently writable`() {
        val bytes = ByteArray(128 * 1024) { (it % 251).toByte() }
        val source = File(directory, "é source.dat").apply { writeBytes(bytes) }
        val destination = File(directory, "private copy.dat")

        val copied = NativeResourceFiles.asByteSource(source.toURI().toURL()).copyTo(NativeResourceFiles.asByteSink(destination))

        assertEquals(bytes.size.toLong(), copied)
        assertContentEquals(bytes, destination.readBytes())
        assertFalse(Files.isSameFile(source.toPath(), destination.toPath()))
        destination.writeText("changed")
        assertContentEquals(bytes, source.readBytes())
    }

    @Test
    fun `existing destinations and append modes retain their stream semantics`() {
        val source = File(directory, "source").apply { writeText("resource") }
        for (append in listOf(false, true)) {
            for (initial in listOf("", "before")) {
                val destination = File(directory, "destination-$append-${initial.length}").apply { writeText(initial) }
                val modes = if (append) arrayOf(FileWriteMode.APPEND) else emptyArray()
                destination.inputStream().use { openDestination ->
                    val copied =
                        NativeResourceFiles
                            .asByteSource(
                                source.toURI().toURL(),
                            ).copyTo(NativeResourceFiles.asByteSink(destination, *modes))

                    val expected = if (append) initial + "resource" else "resource"
                    assertEquals(8L, copied)
                    assertEquals(expected, destination.readText())
                    assertEquals(expected, openDestination.readBytes().decodeToString())
                }
            }
        }
    }

    @Test
    fun `non-file resources retain streaming behavior`() {
        val bytes = "streamed resource".toByteArray()
        val resource =
            URL(
                null,
                "memory:resource",
                object : URLStreamHandler() {
                    override fun openConnection(url: URL) =
                        object : URLConnection(url) {
                            override fun connect() = Unit

                            override fun getInputStream() = ByteArrayInputStream(bytes)
                        }
                },
            )
        val destination = File(directory, "streamed")

        assertEquals(bytes.size.toLong(), NativeResourceFiles.asByteSource(resource).copyTo(NativeResourceFiles.asByteSink(destination)))
        assertContentEquals(bytes, destination.readBytes())
    }
}
