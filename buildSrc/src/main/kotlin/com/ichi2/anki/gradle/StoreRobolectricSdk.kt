// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Keep SDK bytes unchanged, but decompress once instead of in every sandbox and test worker. */
@CacheableTask
abstract class StoreRobolectricSdk : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val sourceJar: RegularFileProperty

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @TaskAction
    fun storeSdk() {
        val destination = outputJar.get().asFile
        destination.parentFile.mkdirs()
        ZipFile(sourceJar.get().asFile).use { source ->
            ZipOutputStream(destination.outputStream().buffered()).use { output ->
                source.entries().asSequence().forEach { original ->
                    val stored = ZipEntry(original).apply {
                        method = ZipEntry.STORED
                        compressedSize = size
                    }
                    output.putNextEntry(stored)
                    source.getInputStream(original).use { it.copyTo(output) }
                    output.closeEntry()
                }
            }
        }
    }
}
