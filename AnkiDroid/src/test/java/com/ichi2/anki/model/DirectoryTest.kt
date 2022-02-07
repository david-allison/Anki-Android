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

import org.acra.util.IOUtils
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.io.path.pathString

/**
 * Tests for [Directory]
 */
class DirectoryTest {
    @Test
    fun passes_if_existing_directory() {
        val path = createTempDirectory().pathString
        MatcherAssert.assertThat(
            "Directory should work with valid directory",
            Directory.createInstance(File(path)),
            not(nullValue())
        )
    }

    @Test
    fun fails_if_does_not_exist() {
        val subdirectory = File(createTempDirectory().pathString, "aa")
        MatcherAssert.assertThat(
            "Directory requires an existing directory",
            Directory.createInstance(subdirectory),
            nullValue()
        )
    }

    @Test
    fun fails_if_file() {
        val dir = kotlin.io.path.createTempFile().pathString
        MatcherAssert.assertThat(
            "file should not become a Directory",
            Directory.createInstance(File(dir)),
            nullValue()
        )
    }

    @Test
    fun has_files_is_false_if_empty() {
        val dir = createValidTempDir()
        MatcherAssert.assertThat(
            "empty directory should not have files",
            dir.hasFiles(),
            equalTo(false)
        )
    }

    @Test
    fun has_files_is_true_if_file() {
        val dir = createValidTempDir()
        IOUtils.writeStringToFile(File(dir.directory, "aa.txt"), "aa")
        MatcherAssert.assertThat(
            "non-empty directory should have files",
            dir.hasFiles(),
            equalTo(true)
        )
    }

    private fun createValidTempDir(): Directory {
        return Directory.createInstance(File(createTempDirectory().pathString))!!
    }
}
