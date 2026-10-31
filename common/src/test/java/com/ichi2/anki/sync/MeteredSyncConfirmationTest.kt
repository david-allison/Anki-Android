// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.sync

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MeteredSyncConfirmationTest {
    @Test
    fun `runs onConfirm immediately when not blocked`() {
        val confirmed = mutableListOf<MeteredSyncPermission>()
        var confirmationRequired = false
        confirmMeteredSync(
            skipPrompt = false,
            shouldBlock = { false },
            onConfirmationRequired = { confirmationRequired = true },
            onConfirm = confirmed::add,
        )
        assertFalse(confirmationRequired)
        assertEquals(listOf(MeteredSyncPermission.USE_PREFERENCES), confirmed)
    }

    @Test
    fun `requests confirmation when blocked without starting sync`() {
        var confirmationRequired = false
        confirmMeteredSync(
            skipPrompt = false,
            shouldBlock = { true },
            onConfirmationRequired = { confirmationRequired = true },
            onConfirm = { error("Sync must wait for the UI to confirm") },
        )
        assertTrue(confirmationRequired)
    }

    @Test
    fun `skipPrompt skips prompt even when metered`() = assertRetryDoesNotPrompt(blocked = true)

    @Test
    fun `skipPrompt on unmetered network runs directly`() = assertRetryDoesNotPrompt(blocked = false)

    private fun assertRetryDoesNotPrompt(blocked: Boolean) {
        val confirmed = mutableListOf<MeteredSyncPermission>()
        var networkChecked = false
        var confirmationRequired = false
        confirmMeteredSync(
            skipPrompt = true,
            shouldBlock = {
                networkChecked = true
                blocked
            },
            onConfirmationRequired = { confirmationRequired = true },
            onConfirm = confirmed::add,
        )
        assertFalse(networkChecked, "Retry permission must not depend on the current network")
        assertFalse(confirmationRequired)
        assertEquals(listOf(MeteredSyncPermission.ALLOW_METERED_SYNC_THIS_TIME), confirmed)
    }
}
