// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.managespace

/** Android application data operations used by the storage-management model. */
interface ApplicationStorage {
    suspend fun sizeInBytes(): Long

    fun clear()
}
