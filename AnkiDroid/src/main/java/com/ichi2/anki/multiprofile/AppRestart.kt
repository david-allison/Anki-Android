// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.multiprofile

import android.content.Context
import androidx.work.WorkManager
import androidx.work.await
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.backupLock
import com.ichi2.anki.common.destinations.LauncherDestination
import com.ichi2.anki.toIntent
import com.ichi2.anki.worker.UniqueWorkNames
import com.ichi2.anki.worker.mediaSyncLock
import com.ichi2.anki.worker.syncLock
import com.jakewharton.processphoenix.ProcessPhoenix
import timber.log.Timber
import java.io.File

/**
 * Restarts the app once background work has finished and the collection is closed.
 *
 * Sync work is cancelled once the sync lock is held, since it carries the current
 * profile's AnkiWeb key. The work locks stay held until ProcessPhoenix kills this
 * process, so nothing can start and reopen the collection in the meantime.
 */
suspend fun safeRestartApp(context: Context) {
    Timber.i("Restarting the app")
    restartAfterBackgroundWork(
        syncLock = syncLock,
        mediaSyncLock = mediaSyncLock,
        backupLock = backupLock,
        cancelSyncWork = {
            WorkManager.getInstance(context).run {
                cancelUniqueWork(UniqueWorkNames.SYNC).await()
                cancelUniqueWork(UniqueWorkNames.SYNC_MEDIA).await()
            }
        },
        closeCollection = { CollectionManager.ensureClosed() },
        restart = { ProcessPhoenix.triggerRebirth(context, LauncherDestination.toIntent(context)) },
    )
}

/** True in the short-lived ProcessPhoenix process, which only relaunches the app. */
fun isPhoenixProcess(): Boolean = isPhoenixProcessName(currentProcessName())

private fun currentProcessName(): String? = runCatching { File("/proc/self/cmdline").readText().trim { it <= ' ' } }.getOrNull()
