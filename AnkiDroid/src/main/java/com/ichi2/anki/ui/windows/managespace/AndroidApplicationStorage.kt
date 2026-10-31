// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.managespace

import android.app.ActivityManager
import android.content.Context
import androidx.core.content.ContextCompat.getSystemService

internal class AndroidApplicationStorage(
    private val context: Context,
) : ApplicationStorage {
    override suspend fun sizeInBytes(): Long = context.getUserDataAndCacheSize()

    override fun clear() {
        getSystemService(context, ActivityManager::class.java)?.clearApplicationUserData()
    }
}
