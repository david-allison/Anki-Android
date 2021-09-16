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

import com.ichi2.async.ProgressSenderAndCancelListener
import com.ichi2.async.TaskDelegate
import com.ichi2.async.TaskListener
import com.ichi2.async.TaskManager
import com.ichi2.libanki.Collection
import java.util.function.Consumer

/* This file exists to ensure that AsyncLayer.kt has no accidental references to com.ichi2.async */

/* Classes to convert from AsyncLayer to the CollectionTask interface */

class TaskListenerBuilder<TResult, TProgress>(private val task: AnkiTask<TProgress, TResult>) {
    var before: Runnable? = null
    var after: Consumer<TResult>? = null
    var onProgressUpdate: Consumer<TProgress>? = null
    var onCancelled: Runnable? = null

    fun before(before: Runnable): TaskListenerBuilder<TResult, TProgress> {
        this.before = before
        return this
    }

    fun after(after: Consumer<TResult>): TaskListenerBuilder<TResult, TProgress> {
        this.after = after
        return this
    }

    fun onProgressUpdate(onProgressUpdate: Consumer<TProgress>): TaskListenerBuilder<TResult, TProgress> {
        this.onProgressUpdate = onProgressUpdate
        return this
    }

    fun onCancelled(onCancelled: Runnable): TaskListenerBuilder<TResult, TProgress> {
        this.onCancelled = onCancelled
        return this
    }

    fun execute() {
        val listenerFromThis = object : TaskListener<TProgress, TResult>() {
            override fun onPreExecute() {
                before?.run()
            }

            override fun onPostExecute(result: TResult) {
                after?.accept(result)
            }

            override fun onProgressUpdate(value: TProgress) {
                onProgressUpdate?.accept(value)
            }

            override fun onCancelled() {
                onCancelled?.run()
            }
        }
        TaskManager.launchCollectionTask(task.toDelegate(), listenerFromThis)
    }
}

/** Converts an AnkiTask to a TaskDelegate */
fun <TProgress, TResult> AnkiTask<TProgress, TResult>.toDelegate(): TaskDelegate<TProgress, TResult> {
    val wrapped: AnkiTask<TProgress, TResult> = this

    return object : TaskDelegate<TProgress, TResult>() {
        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<TProgress>): TResult {
            val executionContext = object : TaskExecutionContext<TProgress> {
                override fun isCancelled(): Boolean = collectionTask.isCancelled
                override val col: Collection = col
                override fun doProgress(progress: TProgress) = collectionTask.doProgress(progress)
            }

            return wrapped.execute(executionContext)
        }
    }
}
