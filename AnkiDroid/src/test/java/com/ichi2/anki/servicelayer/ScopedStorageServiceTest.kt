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

package com.ichi2.anki.servicelayer

import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.model.Directory
import com.ichi2.anki.servicelayer.scopedstorage.MigrateEssentialFilesAlgorithm
import com.ichi2.anki.servicelayer.scopedstorage.MigrateEssentialFilesAlgorithmTest.Companion.assertMigrationInProgress
import com.ichi2.anki.servicelayer.scopedstorage.MigrateEssentialFilesAlgorithmTest.Companion.assertMigrationNotInProgress
import com.ichi2.testutils.AnkiAssert.assertDoesNotThrow
import com.ichi2.testutils.ShadowStatFs
import com.ichi2.testutils.assertThrows
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.kotlin.KStubbing
import org.mockito.kotlin.spy
import java.io.File
import java.io.FileOutputStream
import kotlin.io.path.Path
import kotlin.io.path.pathString

@RunWith(AndroidJUnit4::class)
class ScopedStorageServiceTest : RobolectricTest() {

    private lateinit var destinationPath: String

    override fun useInMemoryDatabase(): Boolean = false

    @Before
    override fun setUp() {
        super.setUp()

        // we need to access 'col' before we start
        col.basicCheck()
        destinationPath = Path(targetContext.getExternalFilesDir(null)!!.canonicalPath, "AnkiDroid-1").pathString

        ShadowStatFs.markAsNonEmpty(File(destinationPath))
    }

    @After
    override fun tearDown() {
        super.tearDown()
        ShadowStatFs.reset()
    }

    @Test
    fun validMigration() {
        assertMigrationNotInProgress()

        val oldDeckPath = getPreferences().getString("deckPath", "")

        migrateEssentialFiles()

        // assert the collection is open, working, and has been moved to the outPath
        assertThat(col.basicCheck(), equalTo(true))
        assertThat(col.path, equalTo(File(destinationPath, "collection.anki2").canonicalPath))

        assertMigrationInProgress()

        // assert that the preferences are updated
        val prefs = getPreferences()
        assertThat("The deck path is updated", prefs.getString("deckPath", ""), equalTo(destinationPath))
        assertThat("The migration source is the original deck path", prefs.getString(ScopedStorageService.PREF_MIGRATION_SOURCE, ""), equalTo(oldDeckPath))
        assertThat("The migration destination is the deck path", prefs.getString(ScopedStorageService.PREF_MIGRATION_DESTINATION, ""), equalTo(destinationPath))
    }

    @Test
    fun notEnoughFreeSpace() {
        ShadowStatFs.reset()

        val ex = assertThrows<MigrateEssentialFilesAlgorithm.UserActionRequiredException.OutOfSpaceException> {
            migrateEssentialFiles()
        }

        assertThat(ex.message, containsString("More free space is required"))
    }

    @Test
    fun sourceIsAlreadyScoped() {
        getPreferences().edit { putString(CollectionHelper.PREF_DECK_PATH, destinationPath) }
        CollectionHelper.getInstance().setColForTests(null)

        val newDestination = Directory.createInstance(File(destinationPath, "again").canonicalFile)!!

        val ex = assertThrows<IllegalStateException> {
            ScopedStorageService.migrateEssentialFiles(targetContext, newDestination)
        }

        assertThat(ex.message, containsString("Directory is already under scoped storage"))
    }

    @Test
    fun ifFolderAlreadyExistsAndIsEmpty() {
        assertThat("destination should not exist ($destinationPath)", File(destinationPath).exists(), equalTo(false))

        assertDoesNotThrow { migrateEssentialFiles() }
    }

    @Test
    fun ifFolderExistsAndIsNotEmpty() {
        File(destinationPath).mkdirs()
        assertThat("destination should exist ($destinationPath)", File(destinationPath).exists(), equalTo(true))

        FileOutputStream(File(destinationPath, "hello.txt")).use {
            it.write(1)
        }

        val ex = assertThrows<IllegalStateException> {
            migrateEssentialFiles()
        }

        assertThat(ex.message, containsString("Target directory was not empty"))
    }

    /**
     * A race condition can occur between closing and locking the collection.
     *
     * We add a retry mechanism to confirm that this works
     */
    @Test
    fun testRetryIfRaceConditionOccurs() {
        var timesCalled = 0

        migrateEssentialFiles {
            Mockito.doAnswer {
                // if it's the first time, open the collection instead of checking for corruption
                // on the retry this is not true
                if (timesCalled == 0) {
                    col.basicCheck()
                }
                timesCalled++
            }.`when`(it).ensureCollectionNotCorrupted(ArgumentMatchers.anyString())
        }

        assertThat(timesCalled, equalTo(3))
    }

    @Test
    fun testRetryIfCopyFails() {
        // TODO: I'm unsure about this one - the directory is no longer empty
        TODO()
    }

    private fun migrateEssentialFiles(stubbing: (KStubbing<MigrateEssentialFilesAlgorithm>.(MigrateEssentialFilesAlgorithm) -> Unit)? = null) {

        fun mock(e: MigrateEssentialFilesAlgorithm): MigrateEssentialFilesAlgorithm {
            return if (stubbing == null) e else spy(e, stubbing)
        }
        ScopedStorageService.migrateEssentialFiles(targetContext, Directory.createInstance(File(destinationPath))!!, ::mock)
    }
}
