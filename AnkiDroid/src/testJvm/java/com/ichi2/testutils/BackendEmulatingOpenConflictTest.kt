// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import com.ichi2.anki.CollectionManager
import com.ichi2.anki.exception.CollectionLockedException
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test

class BackendEmulatingOpenConflictTest : JvmTest() {
    @After
    override fun tearDown() {
        try {
            super.tearDown()
        } finally {
            BackendEmulatingOpenConflict.disable()
        }
    }

    @Test
    fun `enabling conflicts replaces an already initialized backend`() {
        CollectionManager.getBackend()
        BackendEmulatingOpenConflict.enable()

        assertThrows(
            CollectionLockedException::class.java,
        ) { CollectionManager.getColUnsafe() }
    }
}
