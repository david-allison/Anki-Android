// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.CollectionFiles
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.instanceOf
import org.hamcrest.Matchers.nullValue
import org.hamcrest.Matchers.sameInstance
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import java.io.File

@RunWith(ParameterizedRobolectricTestRunner::class)
class CollectionStorageModeTest(
    private val storageMode: String,
) : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.valueOf(storageMode)

    @Test
    fun `withCol opens the selected storage mode`() =
        runTest {
            assertThat(CollectionManager.isOpenUnsafe(), equalTo(false))
            val opened = withCol { this }
            assertStorageMode(opened)
            assertThat(col, sameInstance(opened))
        }

    private fun assertStorageMode(collection: Collection) {
        val databasePath = collection.db.queryString("select file from pragma_database_list where name = 'main'")
        when (getCollectionStorageMode()) {
            CollectionStorageMode.IN_MEMORY_NO_FOLDERS -> {
                assertThat(databasePath, equalTo(""))
                assertThat(collection.collectionFiles, sameInstance(CollectionFiles.InMemory))
                assertThat(collection.collectionFiles.mediaFolder, nullValue())
            }
            CollectionStorageMode.IN_MEMORY_WITH_MEDIA -> {
                assertThat(databasePath, equalTo(""))
                assertThat(collection.collectionFiles, instanceOf(CollectionFiles.InMemoryWithMedia::class.java))
                assertThat(collection.collectionFiles.mediaFolder?.isDirectory, equalTo(true))
            }
            CollectionStorageMode.ON_DISK -> {
                assertThat(collection.collectionFiles, instanceOf(CollectionFiles.FolderBasedCollection::class.java))
                assertThat(File(databasePath).canonicalPath, equalTo(collection.colDb.canonicalPath))
                assertThat(collection.colDb.isFile, equalTo(true))
            }
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun storageModes(): List<String> = CollectionStorageMode.entries.map { it.name }
    }
}
