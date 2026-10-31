// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.startup

import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteFullException
import com.ichi2.anki.InitialActivity.StartupFailure
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.exception.CollectionLockedException
import com.ichi2.anki.exception.StorageAccessException
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbFileTooNewException
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbFullException
import timber.log.Timber

/** Classifies collection errors independently of Android storage and preference checks. */
internal fun collectionOpenFailure(
    error: Exception,
    reportUnexpected: (Exception) -> Unit,
): StartupFailure {
    Timber.w(error)
    return when (error) {
        is CollectionLockedException -> StartupFailure.DatabaseLocked
        is BackendDbFileTooNewException -> StartupFailure.FutureAnkidroidVersion
        is BackendDbFullException, is SQLiteFullException -> StartupFailure.DiskFull
        is BackendDbCorruptException, is SQLiteDatabaseCorruptException -> {
            DatabaseCorruption.isDetected = true
            StartupFailure.DBError(error)
        }
        is StorageAccessException -> StartupFailure.DBError(error)
        else -> {
            reportUnexpected(error)
            StartupFailure.DBError(error)
        }
    }
}
