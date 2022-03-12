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

/** A relative path, with the final component representing the filename */
class RelativeFilePath private constructor(val path: List<String>) {
    val fileName: String get() = path.last()
    companion object {
        fun createInstance(list: List<String>): RelativeFilePath? {
            if (list.isEmpty()) return null
            return RelativeFilePath(list)
        }

        fun fromPaths(rootDir: File, file: DiskFile): RelativeFilePath? =
            fromCanonicalFiles(rootDir.canonicalFile, file.file.canonicalFile)

        private fun fromCanonicalFiles(rootDirectory: File, path: File): RelativeFilePath? {
            var mutablePath = path
            val relativePath = mutableListOf<String>()
            while (mutablePath.parentFile != null && mutablePath.parentFile != rootDirectory) {
                relativePath.add(mutablePath.name)
                mutablePath = mutablePath.parentFile!!
            }

            // was not inside the directory
            if (mutablePath.parentFile == null) {
                return null
            }

            // attempt to create a relative file path
            return createInstance(relativePath)
        }
    }
}
