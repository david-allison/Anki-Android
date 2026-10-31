// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.storage

import com.ichi2.anki.exception.StorageAccessException
import com.ichi2.anki.exception.StorageNotConfiguredException
import com.ichi2.anki.exception.SystemStorageException
import java.io.File
import java.io.IOException

/** Collection path state; Android supplies the persisted path and any failure to choose a default. */
class CollectionDirectory {
    var override: File? = null
    var systemStorageFailure: SystemStorageException? = null

    fun storageDecision(hasStoredPath: Boolean): StorageDecision =
        if (override != null || hasStoredPath) StorageDecision.Decided else StorageDecision.Undecided

    /** Instrumentation uses its own subdirectory and deliberately ignores the temporary override. */
    fun current(
        storedPath: String?,
        instrumentation: Boolean = false,
    ): File {
        if (!instrumentation) override?.let { return it }
        val path = storedPath ?: throw (systemStorageFailure ?: StorageNotConfiguredException())
        return if (instrumentation) File(path, "androidTest") else File(path)
    }
}

/** Creates the collection directory and excludes its media from system media scanning. */
@Synchronized
@Throws(StorageAccessException::class)
fun initializeCollectionDirectory(dir: File) {
    if (!dir.exists() && !dir.mkdirs()) throw StorageAccessException("Failed to create AnkiDroid directory $dir")
    if (!dir.canWrite()) throw StorageAccessException("No write access to AnkiDroid directory $dir")
    val nomedia = File(dir, ".nomedia")
    if (!nomedia.exists()) {
        try {
            nomedia.createNewFile()
        } catch (e: IOException) {
            throw StorageAccessException("Failed to create .nomedia file", e)
        }
    }
}
