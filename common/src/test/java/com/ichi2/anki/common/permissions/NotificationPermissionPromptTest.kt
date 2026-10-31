// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.permissions

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationPermissionPromptTest {
    private var legacyShown = false

    @Test
    fun `enabled notifications skip all prompts on modern and legacy systems`() {
        for (runtime in listOf(true, false)) {
            assertFalse(
                showNotificationPermissionPromptIfNeeded(true, runtime, { error("Must not query rationale") }, ::legacyShown) {
                    error("Must not launch a prompt")
                },
            )
        }
        assertFalse(legacyShown)
    }

    @Test
    fun `runtime prompts can repeat until permanently denied`() {
        val prompts = mutableListOf<NotificationPermissionPrompt>()
        repeat(2) {
            assertTrue(showNotificationPermissionPromptIfNeeded(false, true, { true }, ::legacyShown, prompts::add))
        }
        assertFalse(showNotificationPermissionPromptIfNeeded(false, true, { false }, ::legacyShown, prompts::add))
        assertEquals(List(2) { NotificationPermissionPrompt.RUNTIME_PERMISSION }, prompts)
        assertFalse(legacyShown)
    }

    @Test
    fun `legacy prompts only show once and do not query runtime permissions`() {
        val prompts = mutableListOf<NotificationPermissionPrompt>()
        assertTrue(showNotificationPermissionPromptIfNeeded(false, false, { error("No runtime permission") }, ::legacyShown, prompts::add))
        assertTrue(legacyShown)
        assertFalse(showNotificationPermissionPromptIfNeeded(false, false, { error("No runtime permission") }, ::legacyShown, prompts::add))
        assertEquals(listOf(NotificationPermissionPrompt.LEGACY_SETTINGS), prompts)
    }

    @Test
    fun `a failed legacy prompt launch is not recorded as shown`() {
        assertFailsWith<IllegalStateException> {
            showNotificationPermissionPromptIfNeeded(false, false, { false }, ::legacyShown) { error("Unable to show prompt") }
        }
        assertFalse(legacyShown)
    }
}
