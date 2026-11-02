// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class StoreRobolectricSdkTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `SDK classes and resources retain their names and exact bytes without compression`() {
        val entries = mapOf(
            "android/" to byteArrayOf(),
            "android/example/Outer\$Inner.class" to ByteArray(1024) { it.toByte() },
            "resources.arsc" to ByteArray(8192) { (it % 7).toByte() },
            "assets/日本語.txt" to "Test resource".toByteArray(),
        )
        val input = File(projectDir, "sdk.jar")
        ZipOutputStream(input.outputStream()).use { output ->
            entries.forEach { (name, bytes) ->
                output.putNextEntry(ZipEntry(name))
                output.write(bytes)
                output.closeEntry()
            }
        }
        val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        val task = project.tasks.register("storeSdk", StoreRobolectricSdk::class.java).get()
        task.sourceJar.set(input)
        task.outputJar.set(File(projectDir, "prepared/sdk.jar"))

        task.storeSdk()

        ZipFile(task.outputJar.get().asFile).use { archive ->
            assertEquals(entries.keys, archive.entries().asSequence().map { it.name }.toSet())
            entries.forEach { (name, bytes) ->
                val entry = archive.getEntry(name)
                assertEquals(ZipEntry.STORED, entry.method)
                assertEquals(bytes.size.toLong(), entry.size)
                archive.getInputStream(entry).use { assertArrayEquals(bytes, it.readBytes()) }
            }
        }
    }
}
