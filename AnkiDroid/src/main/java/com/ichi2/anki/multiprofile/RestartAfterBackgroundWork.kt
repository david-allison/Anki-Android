// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.multiprofile

import kotlinx.coroutines.sync.Mutex

/** Stops profile work before a restart, retaining the locks until the process exits. */
internal suspend fun restartAfterBackgroundWork(
    syncLock: Mutex,
    mediaSyncLock: Mutex,
    backupLock: Mutex,
    cancelSyncWork: suspend () -> Unit,
    closeCollection: suspend () -> Unit,
    restart: () -> Unit,
) {
    syncLock.holdUnlessThrows {
        cancelSyncWork()
        mediaSyncLock.holdUnlessThrows {
            backupLock.holdUnlessThrows {
                closeCollection()
                restart()
            }
        }
    }
}

/** Holds this lock while [block] runs, and keeps holding it unless [block] throws. */
private suspend inline fun Mutex.holdUnlessThrows(block: () -> Unit) {
    lock()
    var returned = false
    try {
        block()
        returned = true
    } finally {
        if (!returned) unlock()
    }
}

/** True in the short-lived ProcessPhoenix process, which only relaunches the app. */
internal fun isPhoenixProcessName(name: String?): Boolean = name?.endsWith(":phoenix") == true
