// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.storage

import com.ichi2.anki.exception.StorageAccessException
import com.ichi2.anki.exception.StorageNotConfiguredException
import com.ichi2.anki.exception.SystemStorageException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CollectionDirectoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `an unset path remains undecided and throws instead of choosing a default`() {
        val directory = CollectionDirectory()
        assertEquals(StorageDecision.Undecided, directory.storageDecision(false))
        assertFailsWith<StorageNotConfiguredException> { directory.current(null) }
    }

    @Test
    fun `an unset path rethrows the recorded storage failure`() {
        val failure = SystemStorageException("No external files directory", "unmounted", "diagnostics")
        val directory = CollectionDirectory().apply { systemStorageFailure = failure }
        assertEquals(StorageDecision.Undecided, directory.storageDecision(false))
        assertSame(failure, assertFailsWith<SystemStorageException> { directory.current(null) })
        assertEquals("unmounted", failure.externalStorageState)
        assertEquals("diagnostics", failure.infoUri)
    }

    @Test
    fun `a stored path remains usable despite an earlier startup failure`() {
        val directory = CollectionDirectory().apply { systemStorageFailure = SystemStorageException("Earlier failure", "mounted", null) }
        assertEquals(File("/a/collection/path"), directory.current("/a/collection/path"))
        assertEquals(StorageDecision.Decided, directory.storageDecision(true))
    }

    @Test
    fun `the directory override precedes stored and unset paths`() {
        val override = File("/an/override")
        val directory = CollectionDirectory().apply { this.override = override }
        assertEquals(override, directory.current("/a/collection/path"))
        assertEquals(override, directory.current(null))
        assertEquals(StorageDecision.Decided, directory.storageDecision(false))
    }

    @Test
    fun `instrumentation uses a subdirectory of the stored path rather than the override`() {
        val directory = CollectionDirectory().apply { override = File("/an/override") }
        assertEquals(File("/stored/androidTest"), directory.current("/stored", instrumentation = true))
        assertFailsWith<StorageNotConfiguredException> { directory.current(null, instrumentation = true) }
    }

    @Test
    fun `directory initialization creates missing parents and the media exclusion file`() {
        val directory = File(folder.root, "parent/collection")
        initializeCollectionDirectory(directory)
        assertTrue(directory.isDirectory)
        assertTrue(File(directory, ".nomedia").isFile)
        initializeCollectionDirectory(directory)
        assertTrue(File(directory, ".nomedia").isFile)
    }

    @Test
    fun `a file blocking directory creation is reported as inaccessible storage`() {
        val blocker = folder.newFile("blocker")
        assertFailsWith<StorageAccessException> { initializeCollectionDirectory(File(blocker, "collection")) }
    }

    @Test
    fun `a failure to create the media exclusion file preserves its IO cause`() {
        assertFailsWith<StorageAccessException> { initializeCollectionDirectory(folder.newFile("not-a-directory")) }.also {
            assertTrue(it.cause is java.io.IOException)
        }
    }
}
