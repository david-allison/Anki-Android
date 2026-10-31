// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.sync

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MeteredSyncConfirmationTest {
    @Test
    fun `runs onConfirm immediately when not blocked`() {
        val confirmed = mutableListOf<MeteredSyncPermission>()
        var dialogShown = false
        confirmMeteredSync(
            skipPrompt = false,
            shouldBlock = { false },
            showPrompt = { error("No dialog may be shown") },
            onDialogShown = { dialogShown = true },
            onConfirm = confirmed::add,
        )
        assertFalse(dialogShown)
        assertEquals(listOf(MeteredSyncPermission.USE_PREFERENCES), confirmed)
    }

    @Test
    fun `skipPrompt skips prompt even when metered`() = assertRetryDoesNotPrompt(blocked = true)

    @Test
    fun `skipPrompt on unmetered network runs directly`() = assertRetryDoesNotPrompt(blocked = false)

    private fun assertRetryDoesNotPrompt(blocked: Boolean) {
        val confirmed = mutableListOf<MeteredSyncPermission>()
        var networkChecked = false
        var dialogShown = false
        confirmMeteredSync(
            skipPrompt = true,
            shouldBlock = {
                networkChecked = true
                blocked
            },
            showPrompt = { error("A retry must retain its per-attempt permission") },
            onDialogShown = { dialogShown = true },
            onConfirm = confirmed::add,
        )
        assertFalse(networkChecked, "Retry permission must not depend on the current network")
        assertFalse(dialogShown)
        assertEquals(listOf(MeteredSyncPermission.ALLOW_METERED_SYNC_THIS_TIME), confirmed)
    }
}
