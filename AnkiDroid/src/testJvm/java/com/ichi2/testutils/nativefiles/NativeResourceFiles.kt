// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils.nativefiles

import com.google.common.io.ByteSink
import com.google.common.io.ByteSource
import com.google.common.io.FileWriteMode
import com.google.common.io.Resources
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/** JVM copies of cached resources, retaining a separate writable file for each native runtime. */
object NativeResourceFiles {
    @JvmStatic
    fun asByteSource(url: URL): ByteSource =
        object : ByteSource() {
            private val delegate = Resources.asByteSource(url)

            override fun openStream() = delegate.openStream()

            override fun copyTo(sink: ByteSink): Long {
                val fileSink = sink as? ResourceFileSink
                val source = localFile(url)
                if (source != null && fileSink != null && fileSink.isFreshDestination() && Files.isRegularFile(source)) {
                    Files.copy(source, fileSink.file.toPath(), REPLACE_EXISTING)
                    return fileSink.file.length()
                }
                return delegate.copyTo(sink)
            }
        }

    @JvmStatic
    fun asByteSink(
        file: File,
        vararg modes: FileWriteMode,
    ): ByteSink = ResourceFileSink(file, modes)

    private fun localFile(url: URL): Path? =
        if (url.protocol == "file" && url.authority.isNullOrEmpty() && url.query == null && url.ref == null) {
            Path.of(url.toURI())
        } else {
            null
        }

    private class ResourceFileSink(
        val file: File,
        modes: Array<out FileWriteMode>,
    ) : ByteSink() {
        private val append = FileWriteMode.APPEND in modes
        private val delegate =
            com.google.common.io.Files
                .asByteSink(file, *modes)

        override fun openStream() = delegate.openStream()

        fun isFreshDestination() =
            !append && !Files.exists(file.toPath(), NOFOLLOW_LINKS) && file.absoluteFile.parentFile?.isDirectory == true
    }
}
