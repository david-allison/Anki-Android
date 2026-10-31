// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.startup

import com.ichi2.anki.common.storage.AnkiDroidFolder
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StoragePolicyTest {
    @Test
    fun `a fixed folder is persisted whether or not permissions are granted`() {
        for (folder in AnkiDroidFolder.entries) {
            val policy = StoragePolicy.Fixed(folder)
            assertEquals(folder, policy.folderToPersist(hasRequiredPermissions = true), "$folder: granted")
            assertEquals(folder, policy.folderToPersist(hasRequiredPermissions = false), "$folder: not granted")
        }
    }

    @Test // #13574
    fun `a user choice is deferred until permissions are granted`() {
        val policy = StoragePolicy.UserChoosesOnPermissionScreen
        assertEquals(AnkiDroidFolder.PUBLIC, policy.folderToPersist(hasRequiredPermissions = true), "granted")
        assertNull(policy.folderToPersist(hasRequiredPermissions = false), "not granted: deferred")
    }
}
