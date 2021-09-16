/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

import com.ichi2.libanki.Collection
import com.ichi2.libanki.DB
import timber.log.Timber

/*
    Async Layer on top of the legacy CollectionTask layer
    These classes exist to remove the need for a class hierarchy while using a CollectionTask
 */

abstract class AnkiMethod<TResult> : AnkiTask<Unit, TResult>()

/** Provides methods which are useful when executing a task */
abstract class AnkiTask<TProgress, TResult> {
    private lateinit var executionContext: TaskExecutionContext<TProgress>

    val col: Collection get() = executionContext.col
    fun isCancelled(): Boolean = executionContext.isCancelled()
    fun doProgress(progress: TProgress) = executionContext.doProgress(progress)

    fun execute(executionContext: TaskExecutionContext<TProgress>): TResult {
        this.executionContext = executionContext
        return execute()
    }

    protected abstract fun execute(): TResult

    /** I would prefer these as extension functions, but leaving them here to make the Java Interface nicer */
    fun before(function: Runnable) = TaskListenerBuilder(this).before(function)
    fun after(function: (TResult) -> Unit) = TaskListenerBuilder(this).after(function)
    fun onProgressUpdate(function: (TProgress) -> Unit) = TaskListenerBuilder(this).onProgressUpdate(function)
    fun onCancelled(function: () -> Unit) = TaskListenerBuilder(this).onCancelled(function)
}

interface TaskExecutionContext<T> {
    fun isCancelled(): Boolean
    fun doProgress(progress: T)
    val col: Collection
}

inline fun <T> DB.executeInTransaction2(task: () -> T): T {
    // Ported from code which started the transaction outside the try..finally
    database.beginTransaction()
    try {
        val ret = task()
        if (database.inTransaction()) {
            try {
                database.setTransactionSuccessful()
            } catch (e: Exception) {
                // Unsure if this can happen - copied the structure from endTransaction()
                Timber.w(e)
            }
        } else {
            Timber.w("Not in a transaction. Cannot mark transaction successful.")
        }
        return ret
    } finally {
        DB.safeEndInTransaction(database)
    }
}
