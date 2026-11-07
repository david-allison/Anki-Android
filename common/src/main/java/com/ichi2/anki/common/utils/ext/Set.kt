// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.utils.ext

import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll

/**
 * Waits for a snapshot of the jobs, allowing them to remove themselves from this set on completion.
 */
suspend fun Set<Job>.joinAll() = toList().joinAll()

/** Adds [job] to this set and removes it when it completes. */
fun MutableSet<Job>.registerJob(job: Job) {
    add(job)
    job.invokeOnCompletion { remove(job) }
}
