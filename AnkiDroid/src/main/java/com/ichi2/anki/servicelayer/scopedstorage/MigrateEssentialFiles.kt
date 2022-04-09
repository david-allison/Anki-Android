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
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.exception.RetryableException
import com.ichi2.anki.servicelayer.*
import com.ichi2.anki.servicelayer.ScopedStorageService.PREF_MIGRATION_DESTINATION
import com.ichi2.anki.servicelayer.ScopedStorageService.PREF_MIGRATION_SOURCE
import com.ichi2.anki.servicelayer.scopedstorage.MigrateEssentialFiles.UserActionRequiredException
import com.ichi2.compat.CompatHelper
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Storage
import com.ichi2.libanki.Utils
import timber.log.Timber
import java.io.Closeable
import java.io.File

/**
 * Algorithm class which represents copying the collection and media SQL files to a
 * location under scoped storage.
 * This exists as a class to allow overriding operations for fault injection testing
 *
 * Our main concerns here are ensuring that there are no errors, and the graceful handling of issues.
 * One primary concern is whether the failure case leaves files in the directory.
 *
 * Many of our users are low on space, and leaving "difficult to delete" files in the app private
 * directory is user-hostile.
 *
 * See: [execute]
 *
 * Preconditions (verified inside class):
 * * Collection is not corrupt and can be opened
 * * Collection basic check passes [UserActionRequiredException.CheckDatabaseException]
 * * Collection can be closed and locked
 * * User has space [UserActionRequiredException.OutOfSpaceException]
 * * A migration is not currently taking place
 */
open class MigrateEssentialFiles(
    private val context: Context,
    private val sourcePath: AnkiDroidDirectory,
    private val destinationDirectory: NonLegacyAnkiDroidFolder
) {
    /**
     * After:
     *
     * [PREF_MIGRATION_SOURCE] contains the [AnkiDroidDirectory] with the remaining items to convert
     * [PREF_MIGRATION_DESTINATION] contains an [AnkiDroidDirectory] with the copied collection.anki2/media
     * "deckPath" now points to the new location of the collection in private storage
     */
    fun execute() {
        if (ScopedStorageService.migrationIsInProgress(context)) {
            throw IllegalStateException("Migration is already in progress")
        }

        val destinationPath = destinationDirectory.path

        ensureFolderIsEmpty(destinationPath)

        // ensure the current collection is the one in sourcePath
        ensurePathIsCurrentCollectionPath(sourcePath)

        // Close the collection before we lock the files.
        // ensureCollectionNotCorrupted is not compatible with an already open collection
        closeCollection()

        // Race Condition! - The collection could be opened here before locking (maybe by API?).
        // This is resolved as a RetryableException is thrown if the collection is open

        // open the collection directly and ensure it's not corrupted (must be closed and not locked)
        ensureCollectionNotCorrupted(sourcePath.getCollectionAnki2Path())

        // Lock the collection & journal, to ensure that nobody can open/corrupt it
        lockCollection().use {
            // Copy essential files to new location. Guaranteed to be empty
            for (file in listEssentialFiles().flatMap { it.getFiles(sourcePath.directory.canonicalPath) }) {
                copyTopLevelFile(file, destinationPath)
            }
        }

        val destinationCollectionAnki2Path = destinationPath.getCollectionAnki2Path()

        // Open the collection in the new location, checking for corruption
        ensureCollectionNotCorrupted(destinationCollectionAnki2Path)

        // set the preferences to the new deck path + checks CollectionHelper
        // sets migration variables (migrationIsInProgress will be true)
        updatePreferences(destinationPath)
    }

    /** Copies a file from the top level directory  TODO */
    fun copyTopLevelFile(file: File, destinationPath: AnkiDroidDirectory) {
        CompatHelper.compat.copyFile(file.path, File(destinationPath.directory, file.name).path)
    }

    /**
     * Updates preferences after a successful "essential files" migration.
     * After changing the preferences, we validate them
     */
    private fun updatePreferences(destinationPath: AnkiDroidDirectory) {
        val prefs = AnkiDroidApp.getSharedPrefs(context)

        // keep the old values in case we need to restore them
        val oldPrefValues = listOf(PREF_MIGRATION_SOURCE, PREF_MIGRATION_DESTINATION, "deckPath")
            .map { it to prefs.getString(it, null) }
            .toMap()

        prefs.edit {
            // specify that a migration is in progress
            putString(PREF_MIGRATION_SOURCE, sourcePath.directory.absolutePath)
            putString(PREF_MIGRATION_DESTINATION, destinationPath.directory.absolutePath)
            putString("deckPath", destinationPath.directory.absolutePath)
        }

        // open the collection in the new location - data is now migrated
        try {
            checkMigratedCollection()
        } catch (e: Throwable) {
            // if we can't open the migrated collection, revert the preference change so the user
            // can still use their collection.
            Timber.w("error opening new collection, restoring old values")
            prefs.edit {
                oldPrefValues.forEach {
                    putString(it.key, it.value)
                }
            }
            throw e
        }
    }

    private fun ensureFolderIsEmpty(destinationPath: AnkiDroidDirectory) {
        val listFiles = destinationPath.listFiles()

        if (listFiles.any()) {
            throw IllegalStateException("destination path was non-empty '$destinationPath'")
        }
    }

    open fun checkMigratedCollection() {
        CollectionHelper.getInstance().getCol(context) ?: throw IllegalStateException("collection could not be opened")
    }

    private fun closeCollection() {
        val instance = CollectionHelper.getInstance()
        // this opens col if it wasn't closed
        val col = instance.getCol(context)
        col.close()
    }

    private fun ensurePathIsCurrentCollectionPath(path: AnkiDroidDirectory) {
        val currentCollectionFilePath = getCurrentCollectionPath()
        if (path.directory.canonicalPath != currentCollectionFilePath.directory.canonicalPath) {
            throw IllegalStateException("paths did not match: '$path' and '$currentCollectionFilePath' (Collection)")
        }
    }

    private fun getCurrentCollectionPath(): AnkiDroidDirectory {
        val collectionAnki2Path = File(CollectionHelper.getCollectionPath(context))
        // TODO: !!
        return AnkiDroidDirectory.createInstance(File(collectionAnki2Path.canonicalPath).parent!!)!!
    }

    class LockedCollection private constructor() : Closeable {
        companion object {
            fun createLockedInstance(): LockedCollection {
                Storage.lockCollection()
                return LockedCollection()
            }
        }

        override fun close() {
            Storage.unlockCollection()
        }
    }

    /**
     * Locks
     *
     * @throws IllegalStateException Collection is openable after lock acquired
     */
    fun lockCollection(): Closeable {
        // In Java, file locking is per JVM and not per thread. This means that on macOS,
        // a collection can be opened even if the underlying .anki2 is locked. So we need to lock it
        // with a static
        return createLockedCollection().also {
            // Since we locked the files, we want to ensure that the collection can no longer be opened
            try {
                ensureCollectionNotOpenable()
            } catch (e: Exception) {
                it.close()
                throw e
            }
        }
    }

    @VisibleForTesting
    fun createLockedCollection() = LockedCollection.createLockedInstance()

    private fun ensureCollectionNotOpenable() {
        val lockedCollection: Collection?
        try {
            lockedCollection = CollectionHelper.getInstance().getCol(context)
        } catch (e: Exception) {
            Timber.i("Expected exception thrown: ", e)
            return
        }

        // Unexpected: collection was opened. Close it and report an error.
        // Note: it shouldn't be null - a null value infers a new collection can't be created
        // or if the storage media is removed
        try {
            lockedCollection?.close()
        } catch (e: Exception) {
        }

        throw RetryableException(IllegalStateException("Collection not locked correctly"))
    }

    /**
     * Given the path to a `collection.anki2` which is not open, ensures the collection is usable
     *
     * Otherwise: throws an exception
     *
     * @throws UserActionRequiredException.CheckDatabaseException If "check database" is required
     *
     * This may also fail for the following, less likely reasons:
     * * Collection is already open
     * * Collection directory does not exist
     * * Collection directory is not writable
     * * Error opening collection
     */
    open fun ensureCollectionNotCorrupted(path: CollectionFilePath) {
        var result: Collection? = null
        try {
            // If we already have a collection, store it in
            // this can throw [StorageAccessException]: locked or invalid
            result = CollectionHelper.getInstance().getColFromPath(path, context)
            if (!result.basicCheck()) {
                throw UserActionRequiredException.CheckDatabaseException()
            }
        } finally {
            // this can throw, which ruins the stack trace
            try {
                result?.close()
            } catch (ex: Exception) {
                Timber.w("exception thrown closing database", ex)
            }
        }

        // If close() threw in the finally {}, we want to abort, so call it again
        result!!.close()
    }

    abstract class EssentialFile {
        abstract fun getFiles(sourceDirectory: String): List<File>

        fun spaceRequired(sourceDirectory: String): Long {
            return getFiles(sourceDirectory).sumOf { it.length() }
        }
    }

    class SingleFile(val fileName: String) : EssentialFile() {
        override fun getFiles(sourceDirectory: String): List<File> {
            return listOf(File(sourceDirectory, fileName))
        }
    }

    internal class SqliteDb(val fileName: String) : EssentialFile() {
        override fun getFiles(sourceDirectory: String): List<File> {
            val list = mutableListOf(File(sourceDirectory, fileName))
            val journal = File(sourceDirectory, journalName)
            if (journal.exists()) {
                list.add(journal)
            }
            return list
        }

        // guaranteed to be + "-journal": https://www.sqlite.org/tempfiles.html
        private val journalName = "$fileName-journal"
    }

    abstract class UserActionRequiredException(message: String) : RuntimeException(message) {
        constructor() : this("")

        class CheckDatabaseException : UserActionRequiredException()

        // TODO: i18n for a user message
        class OutOfSpaceException(val available: Long, val required: Long) : UserActionRequiredException("More free space is required. Available: $available. Required: $required") {
            companion object {
                fun throwIfInsufficient(available: Long, required: Long) {
                    if (required > available) {
                        throw OutOfSpaceException(available, required)
                    }
                }
            }
        }
    }

    companion object {
        internal fun listEssentialFiles(): List<EssentialFile> {
            return listOf(
                SqliteDb("collection.anki2"),
                SqliteDb("collection.media.ad.db2"),
                SingleFile(".nomedia")
            )
        }

        /**
         *
         * @param transformAlgo for tests TODO
         */
        @VisibleForTesting
        internal fun migrateEssentialFiles(
            context: Context,
            destinationDirectory: String,
            transformAlgo: ((MigrateEssentialFiles) -> MigrateEssentialFiles)? = null
        ) {
            val collectionPath: CollectionFilePath = CollectionHelper.getInstance().getCol(context).path
            val sourceDirectory = File(collectionPath).parent!!

            if (!ScopedStorageService.isLegacyStorage(sourceDirectory, context)) {
                throw IllegalStateException("Directory is already under scoped storage")
            }

            UserActionRequiredException.OutOfSpaceException.throwIfInsufficient(
                available = Utils.determineBytesAvailable(destinationDirectory),
                required = listEssentialFiles().sumOf { it.spaceRequired(sourceDirectory) } + 10_000_000
            )

            val dir = File(destinationDirectory)
            if (!dir.mkdirs() && dir.exists() && CompatHelper.compat.hasFiles(dir)) {
                throw IllegalStateException("Target directory was not empty: '$destinationDirectory'")
            }

            val destination = NonLegacyAnkiDroidFolder.createInstance(destinationDirectory, context) ?: throw IllegalStateException("Destination folder was not under scoped storage '$destinationDirectory'")

            var algo = MigrateEssentialFiles(
                context,
                AnkiDroidDirectory.createInstance(sourceDirectory)!!, // TODO: !!
                destination
            )

            algo = transformAlgo?.invoke(algo) ?: algo

            RetryableException.retryOnce { algo.execute() }
        }
    }
}
