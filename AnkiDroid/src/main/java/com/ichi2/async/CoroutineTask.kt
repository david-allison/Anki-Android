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

package com.ichi2.async

import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionHelper
import com.ichi2.libanki.Collection
import kotlinx.coroutines.*

private typealias ExceptionHandled = Boolean

/**
 * Executes a TaskDelegate task & enables parallel execution of these tasks
 * Delegates the business knowledge of the task to be performed to the TaskDelegate
 *
 * @param <Progress> The type of progress that is sent by the TaskDelegate. E.g. a Card, a pairWithBoolean.
 * @param <Result>   The type of result that the TaskDelegate sends
 */
class CoroutineTask<Progress : Any, Result : Any>(
    val task: TaskDelegate<Progress, Result>,
    val listener: TaskListener<Progress?, Result>?,
    val exceptionListener: ((State, Exception) -> ExceptionHandled)? = null
) :
    Cancellable,
    ProgressSenderAndCancelListener<Progress> {

    private val job = Job()

    override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
        if (mayInterruptIfRunning || (!job.isActive)) {
            job.cancel()
        }
        return true
    }

    override fun safeCancel(): Boolean {
        return cancel(true)
    }

    private fun getCol(): Collection? {
        return CollectionHelper.getInstance().getCol(AnkiDroidApp.getInstance().applicationContext)
    }

    /** This should be inside [execute], but that showed some states as unused */
    var state = State.PRE_EXECUTE

    fun execute() {
        CoroutineScope(Dispatchers.IO + job).launch {
            try {
                listener?.onPreExecute()
                state = State.EXECUTING
                // throws if collection is not available
                val res: Result = getCol()!!.let { task.executeTask(it, this@CoroutineTask) }
                state = State.POST_EXECUTE
                listener?.onPostExecute(res)
            } catch (e: Exception) {
                if (exceptionListener == null) throw e
                if (!exceptionListener.invoke(state, e)) throw e
            }
        }
    }

    override fun isCancelled(): Boolean = job.isCancelled

    override fun doProgress(value: Progress?) {
        listener?.onProgressUpdate(value)
    }

    enum class State {
        PRE_EXECUTE,
        EXECUTING,
        POST_EXECUTE
    }
}
