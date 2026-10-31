// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import com.ichi2.testutils.OnDiskJvmTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CollectionManagerDirectoryTest : OnDiskJvmTest() {
    @get:Rule
    val unrelatedDirectory = TemporaryFolder()

    @Test
    fun `directory lookup uses the configured collection files without Android preferences`() {
        assertEquals(collectionDirectory.root, CollectionManager.getCollectionDirectory())
    }

    @Test
    fun `deletion closes the collection and only removes its configured directory`() =
        runTest {
            addBasicNote()
            val unrelatedFile = unrelatedDirectory.newFile("keep")

            CollectionManager.deleteCollectionDirectory()

            assertFalse(collectionDirectory.root.exists())
            assertNull(CollectionManager.withOpenColOrNull { noteCount() })
            assertTrue(unrelatedFile.exists())
        }
}
