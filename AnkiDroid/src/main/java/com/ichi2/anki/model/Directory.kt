/*
 *  Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.model

import java.io.File

/**
 * A directory which is assumed to exist (existed when class was instantiated)
 *
 * @see [DiskFile]
 */
class Directory private constructor(val directory: File) {
    /** @see [File.renameTo] */
    fun renameTo(destination: File): Boolean = directory.renameTo(destination)
    override fun toString(): String = directory.canonicalPath
    fun listFiles(): Array<out File> = directory.listFiles() ?: emptyArray()

    /**
     * Whether a directory has files
     * @return false if supplied argument is not a directory, or has no files. True if directory has files
     */
    fun hasFiles(): Boolean = listFiles().any()

    companion object {
        fun createInstance(file: File): Directory? {
            if (!file.exists() || !file.isDirectory) {
                return null
            }
            return Directory(file)
        }
        /** Creates an instance when the preconditions are known to be true */
        fun createInstanceUnsafe(file: File) = Directory(file)
    }
}
