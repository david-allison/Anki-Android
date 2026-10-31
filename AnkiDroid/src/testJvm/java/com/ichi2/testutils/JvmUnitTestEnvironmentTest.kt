// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import org.junit.Test
import kotlin.test.assertFailsWith

class JvmUnitTestEnvironmentTest {
    @Test
    fun `Robolectric runtime is absent`() {
        assertFailsWith<ClassNotFoundException> { Class.forName("org.robolectric.Robolectric") }
    }
}
