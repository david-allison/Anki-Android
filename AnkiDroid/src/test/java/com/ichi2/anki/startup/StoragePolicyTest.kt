// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.startup

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.StoragePermissionSet
import com.ichi2.anki.common.storage.AnkiDroidFolder
import com.ichi2.testutils.EmptyApplication
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class StoragePolicyTest {
    /** The folder persisted on startup for each [StoragePermissionSet], with and without its permissions */
    @SuppressLint("NewApi") // EXTERNAL_MANAGER requires R: only the declared policy is tested
    @Test
    fun `folder persisted on startup by permission set`() {
        val expected =
            listOf(
                Case(StoragePermissionSet.LEGACY_ACCESS, granted = false, persists = AnkiDroidFolder.PUBLIC),
                Case(StoragePermissionSet.LEGACY_ACCESS, granted = true, persists = AnkiDroidFolder.PUBLIC),
                Case(StoragePermissionSet.EXTERNAL_MANAGER, granted = false, persists = null),
                Case(StoragePermissionSet.EXTERNAL_MANAGER, granted = true, persists = AnkiDroidFolder.PUBLIC),
                Case(StoragePermissionSet.APP_PRIVATE, granted = false, persists = AnkiDroidFolder.APP_PRIVATE),
                Case(StoragePermissionSet.APP_PRIVATE, granted = true, persists = AnkiDroidFolder.APP_PRIVATE),
            )

        for (case in expected) {
            val policy = case.permissions.storagePolicy
            assertEquals(case.persists, policy.folderToPersist(case.granted), "${case.permissions}, granted = ${case.granted}")
        }
    }

    private data class Case(
        val permissions: StoragePermissionSet,
        val granted: Boolean,
        /** `null`: the decision is deferred to the user */
        val persists: AnkiDroidFolder?,
    )
}
