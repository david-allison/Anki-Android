// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.permissions

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PermissionRequestsTest {
    private var requested = false
    private var launches = 0
    private var settingsOpened = 0

    @Test
    fun `the first request does not consult the repeat request rationale`() {
        assertTrue(canRequestPermission(::requested) { error("Rationale does not apply yet") })
        requestPermissionOrOpenSettings(::requested, { error("Rationale does not apply yet") }, {
            assertTrue(requested)
            launches++
        }, { settingsOpened++ })
        assertEquals(1, launches)
        assertEquals(0, settingsOpened)
    }

    @Test
    fun `repeat requests launch when the system allows them`() {
        requested = true
        requestPermissionOrOpenSettings(::requested, { true }, { launches++ }, { settingsOpened++ })
        assertTrue(requested)
        assertEquals(1, launches)
        assertEquals(0, settingsOpened)
    }

    @Test
    fun `permanent denial opens settings without launching a request`() {
        requested = true
        assertFalse(canRequestPermission(::requested) { false })
        requestPermissionOrOpenSettings(::requested, { false }, { launches++ }, { settingsOpened++ })
        assertTrue(requested)
        assertEquals(0, launches)
        assertEquals(1, settingsOpened)
    }
}
