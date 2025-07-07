/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.libanki.testutils

import android.content.ContentValues
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ichi2.anki.common.annotations.DuplicatedCode
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.CollectionFiles
import com.ichi2.anki.libanki.DB
import com.ichi2.anki.libanki.LibAnki
import com.ichi2.anki.libanki.Storage.collection
import com.ichi2.anki.libanki.testutils.InMemoryCollectionManager.discardBackend
import kotlinx.coroutines.CoroutineDispatcher
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendFactory
import net.ankiweb.rsdroid.database.AnkiSupportSQLiteDatabase
import timber.log.Timber

interface TestCollectionManager {
    fun getColUnsafe(): com.ichi2.anki.libanki.Collection

    suspend fun discardBackend()

    fun setTestDispatcher(dispatcher: CoroutineDispatcher)
}

object InMemoryCollectionManager : TestCollectionManager {
    private var backend: Backend?
        get() = LibAnki.backend
        set(value) {
            LibAnki.backend = value
        }

    private var collection: Collection?
        get() = LibAnki.collection
        set(value) {
            LibAnki.collection = value
        }

    fun ensureOpenInner() {
        ensureBackendInner()
        if (collection?.dbClosed == false) return
        collection =
            collection(
                collectionFiles = CollectionFiles.InMemory,
                databaseBuilder = { backend -> createDatabaseUsingRustBackend(backend) },
                backend = backend,
            )
    }

    private fun ensureBackendInner() {
        if (backend == null) {
            backend = BackendFactory.getBackend()
        }
    }

    override fun getColUnsafe(): Collection {
        ensureOpenInner()
        return collection!!
    }

    /**
     * Close the currently cached backend and discard it. Useful when enabling the V16 scheduler in the
     * dev preferences, or if the active language changes. Saves and closes the collection if open.
     */
    override suspend fun discardBackend() {
        discardBackendInner()
    }

    /** See [discardBackend]. This must only be run inside the queue. */
    private fun discardBackendInner() {
        ensureClosedInner()
        if (backend != null) {
            backend!!.close()
            backend = null
        }
    }

    private fun ensureClosedInner() {
        if (collection == null) {
            return
        }
        try {
            collection!!.close()
        } catch (exc: Exception) {
            Timber.e("swallowing error on close: $exc")
        }
        collection = null
    }

    override fun setTestDispatcher(dispatcher: CoroutineDispatcher) {
        // intentionally blank
    }
}

/**
 * Wrap a Rust backend connection (which provides an SQL interface).
 * Caller is responsible for opening&closing the database.
 */
@DuplicatedCode("AnkiDroid:createDatabaseUsingRustBackend")
fun createDatabaseUsingRustBackend(backend: Backend): AnkiDroidDB = AnkiDroidDB(AnkiSupportSQLiteDatabase.withRustBackend(backend))

/**
 * Database layer for AnkiDroid. Wraps a [SupportSQLiteDatabase], and provides some helpers on top.
 *
 * @param database the SQLite database containing the collection data.
 */
@DuplicatedCode("AnkiDroid:AnkiDroidDB")
class AnkiDroidDB(
    val database: SupportSQLiteDatabase,
) : DB {
    var mod = false

    override fun close() {
        try {
            database.close()
            Timber.d("Database %s closed = %s", database.path, !database.isOpen)
        } catch (e: Exception) {
            // The pre-framework requery API ate this exception, but the framework API exposes it.
            // We may want to propagate it in the future, but for now maintain the old API and log.
            Timber.e(e, "Failed to close database %s", database.path)
        }
    }

    // Allows to avoid using new Object[]
    override fun query(
        query: String,
        vararg selectionArgs: Any,
    ): Cursor = database.query(query, selectionArgs)

    override fun queryScalar(
        query: String,
        vararg selectionArgs: Any,
    ): Int {
        val scalar: Int
        database.query(query, selectionArgs).use { cursor ->
            if (!cursor.moveToNext()) {
                return 0
            }
            scalar = cursor.getInt(0)
        }
        return scalar
    }

    override fun queryString(
        query: String,
        vararg bindArgs: Any,
    ): String {
        database.query(query, bindArgs).use { cursor ->
            if (!cursor.moveToNext()) {
                throw SQLException("No result for query: $query")
            }
            return cursor.getString(0)
        }
    }

    override fun queryLongScalar(
        query: String,
        vararg bindArgs: Any,
    ): Long {
        var scalar: Long
        database.query(query, bindArgs).use { cursor ->
            if (!cursor.moveToNext()) {
                return 0
            }
            scalar = cursor.getLong(0)
        }
        return scalar
    }

    override fun queryLongList(
        query: String,
        vararg bindArgs: Any,
    ): ArrayList<Long> {
        val results = ArrayList<Long>()
        database.query(query, bindArgs).use { cursor ->
            while (cursor.moveToNext()) {
                results.add(cursor.getLong(0))
            }
        }
        return results
    }

    override fun queryStringList(
        query: String,
        vararg bindArgs: Any,
    ): ArrayList<String> {
        val results = ArrayList<String>()
        database.query(query, bindArgs).use { cursor ->
            while (cursor.moveToNext()) {
                results.add(cursor.getString(0))
            }
        }
        return results
    }

    override fun execute(
        sql: String,
        vararg `object`: Any?,
    ) {
        val s = sql.trim().lowercase()
        // mark modified?
        for (mo in MOD_SQL_STATEMENTS) {
            if (s.startsWith(mo)) {
                break
            }
        }
        database.execSQL(sql, `object`)
    }

    @KotlinCleanup("""Use Kotlin string. Change split so that there is no empty string after last ";".""")
    override fun executeScript(sql: String) {
        val queries = java.lang.String(sql).split(";")
        for (query in queries) {
            database.execSQL(query)
        }
    }

    override fun update(
        table: String,
        values: ContentValues,
        whereClause: String?,
        whereArgs: Array<String>?,
    ): Int = database.update(table, SQLiteDatabase.CONFLICT_NONE, values, whereClause, whereArgs)

    override fun insert(
        table: String,
        values: ContentValues,
    ): Long = database.insert(table, SQLiteDatabase.CONFLICT_NONE, values)

    override val path: String
        get() = database.path ?: ":memory:"

    companion object {
        private val MOD_SQL_STATEMENTS = arrayOf("insert", "update", "delete")
    }
}
