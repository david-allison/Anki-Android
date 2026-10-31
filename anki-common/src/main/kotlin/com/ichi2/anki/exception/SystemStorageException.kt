// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.exception

import android.os.Environment
import timber.log.Timber

/** Captures Android's current media state when the system cannot provide a storage location. */
fun SystemStorageException.Companion.build(
    errorDetail: String,
    infoUri: String? = null,
): SystemStorageException {
    val storageState =
        try {
            Environment.getExternalStorageState()
        } catch (e: Exception) {
            Timber.w(e, "getExternalStorageState")
            "ERROR"
        }
    return SystemStorageException(errorDetail, storageState, infoUri)
}
