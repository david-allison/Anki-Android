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

package com.ichi2.anki.servicelayer.scopedstorage

import android.content.Context
import android.database.sqlite.SQLiteDatabaseCorruptException
import androidx.annotation.CheckResult
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.model.Directory
import com.ichi2.anki.servicelayer.AnkiDroidDirectory
import com.ichi2.anki.servicelayer.NonLegacyAnkiDroidFolder
import com.ichi2.anki.servicelayer.ScopedStorageService
import com.ichi2.anki.servicelayer.ScopedStorageUtils
import com.ichi2.testutils.DatabaseCorruption
import com.ichi2.testutils.TestException
import com.ichi2.testutils.assertThrows
import com.ichi2.testutils.createTransientDirectory
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.KStubbing
import org.mockito.kotlin.spy
import java.io.File
import java.nio.file.Path

/**
 * Test for [MigrateEssentialFilesAlgorithm]
 */
@RunWith(AndroidJUnit4::class)
class MigrateEssentialFilesAlgorithmTest : RobolectricTest() {

    override fun useInMemoryDatabase(): Boolean = false
    private lateinit var defaultCollectionSourcePath: AnkiDroidDirectory

    /** Whether to check the collection to ensure it's still openable */
    var checkCollectionAfter = true

    @Before
    override fun setUp() {
        // had interference between two tests
        CollectionHelper.getInstance().setColForTests(null)
        super.setUp()
        defaultCollectionSourcePath = getMigrationSourcePath()
    }

    @After
    override fun tearDown() {
        super.tearDown()
        if (checkCollectionAfter) {
            assertThat("col is still valid", col.basicCheck())
        }
    }

    @Test
    fun fails_if_path_is_incorrect() {
        // the source path should be the same as the collection path
        val destinationPath = Directory.createInstance(getMigrationDestinationPath())!!
        val algo = getAlgorithm(Directory.createInstanceUnsafe(createTransientDirectory()), destinationPath)
        val exception = assertThrows<IllegalStateException> { algo.execute() }
        assertThat(exception.message, containsString("paths did not match"))
    }

    @Test
    fun what_if_collection_is_currupt() { // TODO: Test name
        checkCollectionAfter = false
        val collectionAnki2Path = DatabaseCorruption.closeAndCorrupt(targetContext)

        val collectionSourcePath = Directory.createInstance(File(collectionAnki2Path).parentFile!!)!!

        assertThrows<SQLiteDatabaseCorruptException> { executeAlgorithmSuccessfully(collectionSourcePath) }

        assertMigrationNotInProgress()
    }

    @Test
    fun fails_if_migration_is_already_in_progress() {
        getPreferences().edit { putString(ScopedStorageService.PREF_MIGRATION_SOURCE, defaultCollectionSourcePath.directory.canonicalPath) }
        assertMigrationInProgress()

        val ex = assertThrows<IllegalStateException> { executeAlgorithmSuccessfully(defaultCollectionSourcePath) }

        assertThat(ex.message, containsString("Migration is already in progress"))
    }

    @Test
    fun if_first_file_is_not_locked() {
        executeAlgorithmSuccessfully(defaultCollectionSourcePath) {
            Mockito.doReturn(object : MigrateEssentialFilesAlgorithm.EssentialFiles() {
                override fun addAndLock(filePath: Path): Unit = throw TestException("testing: something went wrong")
            }).`when`(it).createEssentialFilesInstance()
        }
        TODO("What should happen here?")
    }

    @Test
    fun if_last_file_is_not_locked() {
        val value = object : MigrateEssentialFilesAlgorithm.EssentialFiles() {
            var timesCalled = 0
            override fun addAndLock(filePath: Path) {
                timesCalled++
                if (timesCalled == 3) {
                    throw TestException("testing: something went wrong")
                }
            }
        }

        executeAlgorithmSuccessfully(defaultCollectionSourcePath) {
            Mockito.doReturn(value).`when`(it).createEssentialFilesInstance()
        }

        assertThat("mock should have 3 calls", value.timesCalled, equalTo(3))

        TODO("what should happen here?")
    }

    @Test
    fun if_last_file_is_not_copied() {
        val value = object : MigrateEssentialFilesAlgorithm.EssentialFiles() {
            var timesCalled = 0
            override fun copy(file: EssentialFile, destinationPath: String) {
                timesCalled++
                if (timesCalled == 3) {
                    throw TestException("testing: something went wrong")
                }
                super.copy(file, destinationPath)
            }
        }

        executeAlgorithmSuccessfully(defaultCollectionSourcePath) {
            Mockito.doReturn(value).`when`(it).createEssentialFilesInstance()
        }

        assertThat("mock should have 3 calls", value.timesCalled, equalTo(3))

        TODO("What should happen here")
    }

    @Test
    fun if_first_file_is_not_copied() {
        val value = object : MigrateEssentialFilesAlgorithm.EssentialFiles() {
            var timesCalled = 0
            override fun copy(file: EssentialFile, destinationPath: String) {
                timesCalled++
                throw TestException("something went wrong")
            }
        }

        executeAlgorithmSuccessfully(defaultCollectionSourcePath) {
            Mockito.doReturn(value).`when`(it).createEssentialFilesInstance()
        }

        assertThat("mock should have been called", value.timesCalled, not(equalTo(0)))

        TODO("What should happen here")
    }

    @Test
    fun fails_if_collection_can_still_be_opened() {
        val nonLockingFiles = object : MigrateEssentialFilesAlgorithm.EssentialFiles() {
            override fun addAndLock(filePath: Path) {
                // don't lock the file - so collection can still be opened
            }
        }

        val ex = assertThrows<IllegalStateException> {
            executeAlgorithmSuccessfully(defaultCollectionSourcePath) {
                Mockito.doReturn(nonLockingFiles).`when`(it).createEssentialFilesInstance()
            }
        }

        assertThat(ex.message, containsString("Collection not locked correctly"))
    }

    @Test
    fun prefs_are_restored_if_reopening_fails() {
        // after preferences are set, we make one final check with these new preferences
        // if this check fails, we want to revert the changes to preferences that we made
        val collectionSourcePath = getMigrationSourcePath()

        val prefKeys = listOf(ScopedStorageService.PREF_MIGRATION_SOURCE, ScopedStorageService.PREF_MIGRATION_DESTINATION, "deckPath")
        val oldPrefValues = prefKeys
            .map { it to getPreferences().getString(it, null) }
            .toMap()

        assertThrows<TestException> {
            executeAlgorithmSuccessfully(collectionSourcePath) {
                Mockito.doThrow(TestException("simulating final collection open failure")).`when`(it).checkMigratedCollection()
            }
        }

        oldPrefValues.forEach {
            assertThat("Pref ${it.key} should be unchanged", getPreferences().getString(it.key, null), equalTo(it.value))
        }

        assertMigrationNotInProgress()
    }

    @Test
    fun successful_migration_with_no_media() {
        assertMigrationNotInProgress()

        this.addNoteUsingBasicModel("Hello", "World")

        val collectionSourcePath = getMigrationSourcePath()

        val oldDeckPath = getPreferences().getString("deckPath", "")

        val outPath = executeAlgorithmSuccessfully(collectionSourcePath)

        // assert the collection is open, working, and has been moved to the outPath
        assertThat(col.basicCheck(), equalTo(true))
        assertThat(col.path, equalTo(File(outPath, "collection.anki2").canonicalPath))

        assertMigrationInProgress()

        // assert that the preferences are updated
        val prefs = getPreferences()
        assertThat("The deck path is updated", prefs.getString("deckPath", ""), equalTo(outPath.canonicalPath))
        assertThat("The migration source is the original deck path", prefs.getString(ScopedStorageService.PREF_MIGRATION_SOURCE, ""), equalTo(oldDeckPath))
        assertThat("The migration destination is the deck path", prefs.getString(ScopedStorageService.PREF_MIGRATION_DESTINATION, ""), equalTo(outPath.canonicalPath))

        // TODO: Consider a separate test, doing this during the preference change
        assertThat(".nomedia should be copied", File(outPath.canonicalPath, ".nomedia").exists(), equalTo(true))

        assertThat("card still exists", col.cardCount(), equalTo(1))
    }

    @Test
    fun fails_with_exception_on_no_media() {
        // TODO: What does this mean?
        throw NotImplementedError()
    }

    /**
     * Executes the collection migration algorithm, moving from the local test directory /AnkiDroid, to /migration
     * This is only the initial stage which does not delete data
     */
    private fun executeAlgorithmSuccessfully(
        ankiDroidFolder: AnkiDroidDirectory,
        stubbing: (KStubbing<MigrateEssentialFilesAlgorithm>.(MigrateEssentialFilesAlgorithm) -> Unit)? = null
    ): File {
        val destinationPath = getMigrationDestinationPath()
        destinationPath.mkdirs()

        var algo = getAlgorithm(ankiDroidFolder, Directory.createInstance(destinationPath)!!)

        if (stubbing != null) {
            algo = spy(algo, stubbing)
        }
        algo.execute()

        return destinationPath
    }

    private fun getMigrationDestinationPath(): File {
        return ScopedStorageUtils.getMigrationDestinationPath(targetContext)
    }

    private fun getMigrationSourcePath(): AnkiDroidDirectory =
        Directory.createInstance(ScopedStorageUtils.getMigrationSourcePath(col))!!

    @CheckResult
    private fun getAlgorithm(sourcePath: AnkiDroidDirectory, destinationPath: Directory): MigrateEssentialFilesAlgorithm {
        val destinationDirectory = NonLegacyAnkiDroidFolder.createInstance(destinationPath, targetContext) ?: throw IllegalStateException("'$destinationPath' was not under scoped storage")
        return MigrateEssentialFilesAlgorithm(targetContext, sourcePath, destinationDirectory)
    }

    companion object {

        private fun RobolectricTest.migrationIsInProgress() =
            getMigrationPreferences(this.targetContext).migrationInProgress

        private fun getMigrationPreferences(ctx: Context): ScopedStorageService.UserDataMigrationPreferences {
            return ScopedStorageService.UserDataMigrationPreferences.createInstance(AnkiDroidApp.getSharedPrefs(ctx))
        }

        fun RobolectricTest.assertMigrationInProgress() {
            assertThat("the migration should be in progress", migrationIsInProgress(), equalTo(true))
        }

        fun RobolectricTest.assertMigrationNotInProgress() {
            assertThat("the migration should not be in progress", migrationIsInProgress(), equalTo(false))
        }
    }
}
