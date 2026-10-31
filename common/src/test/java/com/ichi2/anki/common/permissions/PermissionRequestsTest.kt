// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.permissions

import org.junit.Test
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty0
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PermissionRequestsTest {
    private var requested = false

    // Property references work without kotlin-reflect; accessing getter/setter does not.
    private val requestedFlag =
        object : KMutableProperty0<Boolean> by ::requested {
            override val getter: KProperty0.Getter<Boolean>
                get() = error("Permission flags must use get() without reflection")
            override val setter: KMutableProperty0.Setter<Boolean>
                get() = error("Permission flags must use set() without reflection")
        }
    private var launches = 0
    private var settingsOpened = 0

    @Test
    fun `the first request does not consult the repeat request rationale`() {
        assertTrue(canRequestPermission(requestedFlag) { error("Rationale does not apply yet") })
        requestPermissionOrOpenSettings(requestedFlag, { error("Rationale does not apply yet") }, {
            assertTrue(requested)
            launches++
        }, { settingsOpened++ })
        assertEquals(1, launches)
        assertEquals(0, settingsOpened)
    }

    @Test
    fun `repeat requests launch when the system allows them`() {
        requested = true
        requestPermissionOrOpenSettings(requestedFlag, { true }, { launches++ }, { settingsOpened++ })
        assertTrue(requested)
        assertEquals(1, launches)
        assertEquals(0, settingsOpened)
    }

    @Test
    fun `permanent denial opens settings without launching a request`() {
        requested = true
        assertFalse(canRequestPermission(requestedFlag) { false })
        requestPermissionOrOpenSettings(requestedFlag, { false }, { launches++ }, { settingsOpened++ })
        assertTrue(requested)
        assertEquals(0, launches)
        assertEquals(1, settingsOpened)
    }
}
