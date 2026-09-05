// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.annotation.SuppressLint
import android.os.Environment
import com.ichi2.anki.common.permissions.isExternalStorageManager
import com.ichi2.utils.Permissions
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import java.io.File

/** The default collection path in public storage: `~/AnkiDroid` */
val publicCollectionPath: String
    get() = File(Environment.getExternalStorageDirectory(), "AnkiDroid").absolutePath

/** A build declaring `MANAGE_EXTERNAL_STORAGE` in its manifest: every flavor except `play` */
fun withManageExternalStorageInManifest(block: () -> Unit) {
    mockkObject(Permissions)
    every { Permissions.canManageExternalStorage(any()) } returns true
    try {
        block()
    } finally {
        unmockkObject(Permissions)
    }
}

/** 'All files access' (`MANAGE_EXTERNAL_STORAGE`) is granted */
@SuppressLint("NewApi") // isExternalStorageManager requires R: callers run at API 30 or above
fun withAllFilesAccess(block: () -> Unit) {
    mockkStatic(::isExternalStorageManager)
    every { isExternalStorageManager() } returns true
    try {
        block()
    } finally {
        unmockkStatic(::isExternalStorageManager)
    }
}
