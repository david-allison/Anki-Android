// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils.nativefiles

import com.google.common.io.ByteSink
import com.google.common.io.ByteSource
import com.google.common.io.FileWriteMode
import com.google.common.io.Resources
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
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
                if (source != null && fileSink != null && Files.isRegularFile(source)) {
                    if (fileSink.isFreshDestination()) {
                        Files.copy(source, fileSink.file.toPath(), REPLACE_EXISTING)
                        return fileSink.file.length()
                    }
                    if (fileSink.canOverwriteExisting()) return copyIntoExisting(source, fileSink.file)
                }
                return delegate.copyTo(sink)
            }
        }

    @JvmStatic
    fun asByteSink(
        file: File,
        vararg modes: FileWriteMode,
    ): ByteSink = ResourceFileSink(file, modes)

    /** Keep the existing inode: Robolectric creates its empty library file before copying. */
    private fun copyIntoExisting(
        source: Path,
        destination: File,
    ): Long =
        FileInputStream(source.toFile()).use { input ->
            FileOutputStream(destination).use { output ->
                val size = input.channel.size()
                var copied = 0L
                while (copied < size) {
                    val count = input.channel.transferTo(copied, size - copied, output.channel)
                    if (count == 0L) break
                    copied += count
                }
                // A provider may decline channel transfer; also consume any bytes added meanwhile.
                input.channel.position(copied)
                copied + input.copyTo(output)
            }
        }

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

        fun canOverwriteExisting() = !append && Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS)

        fun isFreshDestination() =
            !append && !Files.exists(file.toPath(), NOFOLLOW_LINKS) && file.absoluteFile.parentFile?.isDirectory == true
    }
}
